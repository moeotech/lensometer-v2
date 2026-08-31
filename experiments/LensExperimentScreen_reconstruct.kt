package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.analysis.LensAnalyzer
import com.example.model.LensMeasurementResult
import com.example.model.LensGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.RotatedRect
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.*

enum class LensExperimentPhase {
    ALIGN_NO_LENS,
    CAPTURE_NO_LENS,
    ALIGN_LENS,
    CAPTURE_LENS,
    PROCESSING,
    RESULTS
}

@Composable
fun LensExperimentScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(LensExperimentPhase.ALIGN_NO_LENS) }
    
    val noLensFrames = remember { mutableListOf<Bitmap>() }
    val withLensFrames = remember { mutableListOf<Bitmap>() }
    
    var previewRef by remember { mutableStateOf<Preview?>(null) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var camera2ControlRef by remember { mutableStateOf<Camera2CameraControl?>(null) }
    
    var focusDistance by remember { mutableStateOf(0f) }
    
    val maxRuns = 3
    var runResults by remember { mutableStateOf(mutableListOf<LensMeasurementResult>()) }
    var frameCaptureCallback by remember { mutableStateOf<((ImageProxy) -> Unit)?>(null) }
    var alignMessage by remember { mutableStateOf("DETECTING LENS...") }
    var isStable by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var autoCaptureTriggered by remember { mutableStateOf(false) }
    var detectedEllipse by remember { mutableStateOf<RotatedRect?>(null) }
    var stableLensGeom by remember { mutableStateOf<LensGeometry?>(null) }
    var imgW by remember { mutableIntStateOf(1) }
    var imgH by remember { mutableIntStateOf(1) }
    
    val ellipseHistory = remember { mutableListOf<RotatedRect>() }

    LaunchedEffect(Unit) {
        if (!OpenCVLoader.initDebug()) {
            android.util.Log.e("OpenCV", "Initialization failed")
        }
    }

    LaunchedEffect(isStable, phase) {
        if (isStable && phase == LensExperimentPhase.ALIGN_LENS) {
            autoCaptureTriggered = false
            progress = 0f
            val steps = 20
            val stepTime = 1000L / steps
            for (i in 1..steps) {
                delay(stepTime)
                progress = i.toFloat() / steps
            }
            autoCaptureTriggered = true
        } else {
            progress = 0f
            autoCaptureTriggered = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var isDisposed = false
        var imageAnalysisRef: ImageAnalysis? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (isDisposed) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isDisposed) return@postDelayed
                val preview = Preview.Builder().build()
                previewRef = preview
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                if (phase == LensExperimentPhase.ALIGN_LENS) {
                                    val bmp = proxyToBitmap(imageProxy)
                                    if (bmp != null) {
                                        imgW = bmp.width
                                        imgH = bmp.height
                                        val ell = detectLensEllipse(bmp)
                                        detectedEllipse = ell
                                        if (ell != null) {
                                            ellipseHistory.add(ell)
                                            if (ellipseHistory.size > 10) {
                                                ellipseHistory.removeAt(0)
                                            }
                                            
                                            val cx = ell.center.x
                                            val cy = ell.center.y
                                            val w = imgW
                                            val h = imgH
                                            
                                            // 1. Check Centering
                                            if (cx < w * 0.4) alignMessage = "MOVE RIGHT"
                                            else if (cx > w * 0.6) alignMessage = "MOVE LEFT"
                                            else if (cy < h * 0.4) alignMessage = "MOVE DOWN"
                                            else if (cy > h * 0.6) alignMessage = "MOVE UP"
                                            else {
                                                // 2. Check Size (Distance)
                                                val sizeRatio = max(ell.size.width, ell.size.height) / min(w, h).toDouble()
                                                if (sizeRatio < 0.45) {
                                                    alignMessage = "MOVE CLOSER"
                                                    isStable = false
                                                } else if (sizeRatio > 0.65) {
                                                    alignMessage = "MOVE FARTHER"
                                                    isStable = false
                                                } else {
                                                    // 3. Check Tilt (Aspect Ratio)
                                                    val aspect = max(ell.size.width, ell.size.height) / min(ell.size.width, ell.size.height)
                                                    if (aspect > 1.3) {
                                                        alignMessage = "REDUCE TILT"
                                                        isStable = false
                                                    } else {
                                                        // 4. Temporal Stability
                                                        if (ellipseHistory.size == 10) {
                                                            val meanCx = ellipseHistory.map { it.center.x }.average()
                                                            val meanCy = ellipseHistory.map { it.center.y }.average()
                                                            val meanW = ellipseHistory.map { it.size.width }.average()
                                                            val meanH = ellipseHistory.map { it.size.height }.average()
                                                            val meanAngle = ellipseHistory.map { it.angle }.average()
                                                            
                                                            val stdCx = sqrt(ellipseHistory.map { (it.center.x - meanCx).pow(2) }.average())
                                                            val stdCy = sqrt(ellipseHistory.map { (it.center.y - meanCy).pow(2) }.average())
                                                            val stdW = sqrt(ellipseHistory.map { (it.size.width - meanW).pow(2) }.average())
                                                            val stdH = sqrt(ellipseHistory.map { (it.size.height - meanH).pow(2) }.average())
                                                            val stdAngle = sqrt(ellipseHistory.map { (it.angle - meanAngle).pow(2) }.average())
                                                            
                                                            if (stdCx <= 15.0 && stdCy <= 15.0 && stdW <= 15.0 && stdH <= 15.0 && stdAngle <= 7.0) {
                                                                alignMessage = "HOLD STILL"
                                                                isStable = true
                                                                stableLensGeom = LensGeometry(meanCx, meanCy, meanW, meanH, meanAngle)
                                                            } else {
                                                                alignMessage = "STABILIZING..."
                                                                isStable = false
                                                            }
                                                        } else {
                                                            alignMessage = "STABILIZING..."
                                                            isStable = false
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            alignMessage = "PLACE LENS IN CIRCLE"
                                            isStable = false
                                            ellipseHistory.clear()
                                        }
                                    }
                                } else {
                                    isStable = true
                                }
                                frameCaptureCallback?.invoke(imageProxy)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }
                imageAnalysisRef = imageAnalysis
                try {
                    previewRef?.let { cameraProvider.unbind(it) }
                    imageAnalysisRef?.let { cameraProvider.unbind(it) }
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraControlRef = camera.cameraControl
                    camera2ControlRef = Camera2CameraControl.from(camera.cameraControl)
                } catch (exc: Exception) {}
            }, 300)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            isDisposed = true
            imageAnalysisRef?.clearAnalyzer()
            try {
                if (cameraProviderFuture.isDone) {
                    val provider = cameraProviderFuture.get()
                    previewRef?.let { provider.unbind(it) }
                    imageAnalysisRef?.let { provider.unbind(it) }
                }
            } catch (e: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    suspend fun runCaptureSequence(targetList: MutableList<Bitmap>, lockAE: Boolean) {
        targetList.clear()
        
        if (lockAE) {
            // Let auto expose converge first
            camera2ControlRef?.let { c2c ->
                val builder = CaptureRequestOptions.Builder()
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                c2c.captureRequestOptions = builder.build()
            }
            
            delay(1000)
            
            // Lock Exposure and WB
            camera2ControlRef?.let { c2c ->
                val builder = CaptureRequestOptions.Builder()
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                c2c.captureRequestOptions = builder.build()
            }
            delay(500)
        }
        
        withContext(Dispatchers.Default) {
            for (i in 0 until 30) {
                var capturedBitmap: Bitmap? = null
                val lock = java.util.concurrent.CountDownLatch(1)
                frameCaptureCallback = { proxy ->
                    if (capturedBitmap == null) {
                        capturedBitmap = proxyToBitmap(proxy)
                        lock.countDown()
                    }
                }
                lock.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                frameCaptureCallback = null
                
                capturedBitmap?.let { targetList.add(it) }
                delay(10)
            }
        }
    }

    fun restoreAuto() {
        camera2ControlRef?.let { c2c ->
            val builder = CaptureRequestOptions.Builder()
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            c2c.captureRequestOptions = builder.build()
        }
    }

    LaunchedEffect(autoCaptureTriggered) {
        if (autoCaptureTriggered && phase == LensExperimentPhase.ALIGN_LENS) {
            phase = LensExperimentPhase.CAPTURE_LENS
            alignMessage = "MEASURING..."
            runCaptureSequence(withLensFrames, lockAE = false)
            restoreAuto()
            phase = LensExperimentPhase.PROCESSING
            val res = withContext(Dispatchers.Default) {
                LensAnalyzer.analyze(noLensFrames, withLensFrames, stableLensGeom)
            }
            runResults.add(res)
            phase = LensExperimentPhase.RESULTS
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (phase == LensExperimentPhase.ALIGN_NO_LENS || phase == LensExperimentPhase.ALIGN_LENS) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewRef?.setSurfaceProvider(this.surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    previewRef?.setSurfaceProvider(view.surfaceProvider)
                }
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val radius = min(size.width, size.height) * 0.45f
                
                if (phase == LensExperimentPhase.ALIGN_NO_LENS) {
                    drawCircle(
                        color = Color.Yellow,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f)
                    )
                    drawLine(Color.Red, Offset(cx - 20, cy), Offset(cx + 20, cy), 2f)
                    drawLine(Color.Red, Offset(cx, cy - 20), Offset(cx, cy + 20), 2f)
                } else {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(path, Color.White)
                    
                    val ringColor = if (detectedEllipse == null) Color.LightGray
                    else if (isStable) Color.Green
                    else if (alignMessage == "STABILIZING...") Color(0xFFFFA500)
                    else Color.Red
                    
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 12f)
                    )
                    
                    if (progress > 0f) {
                        drawArc(
                            color = Color(0xFF006400),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = 12f)
                        )
                    }
                    
                    val ell = detectedEllipse
                    if (ell != null && imgW > 1 && imgH > 1) {
                        val scaleX = size.width / imgW
                        val scaleY = size.height / imgH
                        val rrx = ell.center.x * scaleX
                        val rry = ell.center.y * scaleY
                        val rw = ell.size.width * scaleX / 2.0f
                        val rh = ell.size.height * scaleY / 2.0f
                        
                        withTransform({
                            translate(rrx.toFloat(), rry.toFloat())
                            rotate(ell.angle.toFloat())
                        }) {
                            drawOval(
                                color = if (isStable) Color.Green else Color.Red,
                                topLeft = Offset(-rw.toFloat(), -rh.toFloat()),
                                size = androidx.compose.ui.geometry.Size((rw*2).toFloat(), (rh*2).toFloat()),
                                style = Stroke(width = 6f)
                            )
                            drawLine(Color.White, Offset(-15f, 0f), Offset(15f, 0f), 4f)
                            drawLine(Color.White, Offset(0f, -15f), Offset(0f, 15f), 4f)
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (phase == LensExperimentPhase.ALIGN_NO_LENS) "ALIGN PRINTED TARGET (NO LENS)" else alignMessage,
                    color = if (phase == LensExperimentPhase.ALIGN_LENS) Color.Black else Color.White,
                    fontSize = if (phase == LensExperimentPhase.ALIGN_LENS) 24.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = if (phase == LensExperimentPhase.ALIGN_LENS) Modifier.padding(16.dp) else Modifier.background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(16.dp)
                )
                if (phase == LensExperimentPhase.ALIGN_NO_LENS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                phase = LensExperimentPhase.CAPTURE_NO_LENS
                                runCaptureSequence(noLensFrames, lockAE = true)
                                phase = LensExperimentPhase.ALIGN_LENS
                            }
                        },
                        enabled = isStable
                    ) {
                        Text("CAPTURE BASE")
                    }
                }
            }
        } else if (phase == LensExperimentPhase.CAPTURE_NO_LENS || phase == LensExperimentPhase.CAPTURE_LENS || phase == LensExperimentPhase.PROCESSING) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(if (phase == LensExperimentPhase.PROCESSING) "PROCESSING OPTICS..." else "CAPTURING LENS...", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else if (phase == LensExperimentPhase.RESULTS) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Text("MEASUREMENT RESULTS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    for ((i, res) in runResults.withIndex()) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.DarkGray)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("RUN ${i + 1}", color = Color.Cyan, fontWeight = FontWeight.Bold)
                                if (res.confidence.startsWith("FAILED")) {
                                    Text(res.confidence, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else {
                                    ResultRow("SPH", String.format("%.2f D", res.sph))
                                    ResultRow("CYL", String.format("%.2f D", res.cyl))
                                    ResultRow("AXIS", String.format("%.0f°", res.axis))
                                    ResultRow("CONFIDENCE", res.confidence)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("OPTICAL FIELD", color = Color.White, fontWeight = FontWeight.Bold)
                                    val magFactor = 3.0f
                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(res.imageWidth.toFloat() / res.imageHeight.toFloat()).border(1.dp, Color.Gray)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val scaleX = size.width / res.imageWidth
                                            val scaleY = size.height / res.imageHeight
                                            
                                            drawCircle(Color.DarkGray, res.lensRadius.toFloat() * scaleX, Offset(res.geometricCenterX.toFloat() * scaleX, res.geometricCenterY.toFloat() * scaleY), style = Stroke(2f))
                                            drawCircle(Color.Magenta, 10f, Offset(res.opticalCenterX.toFloat() * scaleX, res.opticalCenterY.toFloat() * scaleY))
                                            
                                            val oc = Offset(res.opticalCenterX.toFloat() * scaleX, res.opticalCenterY.toFloat() * scaleY)
                                            val axLen = res.lensRadius.toFloat() * scaleX
                                            val rad1 = res.p1Angle * Math.PI / 180.0
                                            val rad2 = res.p2Angle * Math.PI / 180.0
                                            drawLine(Color.Cyan, oc - Offset((cos(rad1)*axLen).toFloat(), (sin(rad1)*axLen).toFloat()), oc + Offset((cos(rad1)*axLen).toFloat(), (sin(rad1)*axLen).toFloat()), 3f)
                                            drawLine(Color.Yellow, oc - Offset((cos(rad2)*axLen).toFloat(), (sin(rad2)*axLen).toFloat()), oc + Offset((cos(rad2)*axLen).toFloat(), (sin(rad2)*axLen).toFloat()), 3f)
                                            
                                            for (v in res.vectors) {
                                                val start = Offset(v.rx.toFloat() * scaleX, v.ry.toFloat() * scaleY)
                                                val dx = (v.ox - v.rx).toFloat() * scaleX * magFactor
                                                val dy = (v.oy - v.ry).toFloat() * scaleY * magFactor
                                                val end = start + Offset(dx, dy)
                                                drawLine(Color.Red, start, end, 2f)
                                                drawCircle(Color.White, 3f, start)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    val validRuns = runResults.filter { !it.confidence.startsWith("FAILED") }
                    
                    if (validRuns.size >= 3) {
                        Text("REPEATABILITY GATES", color = Color.White, fontWeight = FontWeight.Bold)
                        
                        val p1Mean = validRuns.map { it.p1 }.average()
                        val p2Mean = validRuns.map { it.p2 }.average()
                        val p1Std = Math.sqrt(validRuns.map { Math.pow(it.p1 - p1Mean, 2.0) }.average())
                        
                        var sumSin = 0.0
                        var sumCos = 0.0
                        validRuns.forEach {
                            val rad = 2 * it.p1Angle * Math.PI / 180.0
                            sumSin += sin(rad)
                            sumCos += cos(rad)
                        }
                        val meanAxisRad = Math.atan2(sumSin, sumCos) / 2.0
                        var meanAxisDeg = meanAxisRad * 180.0 / Math.PI
                        if (meanAxisDeg < 0) meanAxisDeg += 180.0
                        val R = Math.sqrt(sumSin*sumSin + sumCos*sumCos) / validRuns.size
                        val angularDev = Math.sqrt(-2.0 * Math.log(R)) * 180.0 / Math.PI / 2.0
                        
                        val ocxMean = validRuns.map { it.opticalCenterX }.average()
                        val ocyMean = validRuns.map { it.opticalCenterY }.average()
                        val ocStd = Math.sqrt(validRuns.map { hypot(it.opticalCenterX - ocxMean, it.opticalCenterY - ocyMean).pow(2) }.average())
                        
                        ResultRow("P1 Mean", String.format("%.5f ± %.5f", p1Mean, p1Std))
                        ResultRow("P2 Mean", String.format("%.5f", p2Mean))
                        ResultRow("AXIS Mean", String.format("%.0f° ± %.1f°", meanAxisDeg, angularDev))
                        ResultRow("OPT CENTER Std", String.format("%.1f px", ocStd))
                        
                        val pass = (p1Std < 0.02 && angularDev < 15.0 && ocStd < 100.0)
                        Text(
                            if (pass) "REPEATABILITY PASS" else "REPEATABILITY FAILED", 
                            color = if (pass) Color.Green else Color.Red, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    
                    if (runResults.size < maxRuns) {
                        Button(
                            onClick = { phase = LensExperimentPhase.ALIGN_NO_LENS },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Text("START RUN ${runResults.size + 1}")
                        }
                    } else {
                        Button(
                            onClick = { runResults.clear(); phase = LensExperimentPhase.ALIGN_NO_LENS },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Text("RESET EXPERIMENT")
                        }
                    }
                }
            }
        }
    }
}

fun proxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes
    val yBuffer = planeProxy[0].buffer
    val uBuffer = planeProxy[1].buffer
    val vBuffer = planeProxy[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()
    
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    
    val matrix = android.graphics.Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun detectLensEllipse(bitmap: Bitmap): RotatedRect? {
    val mat = org.opencv.core.Mat()
    org.opencv.android.Utils.bitmapToMat(bitmap, mat)
    val gray = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY)
    org.opencv.imgproc.Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(9.0, 9.0), 2.0, 2.0)
    
    val edges = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.Canny(gray, edges, 50.0, 150.0)
    
    val contours = mutableListOf<org.opencv.core.MatOfPoint>()
    val hierarchy = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.findContours(edges, contours, hierarchy, org.opencv.imgproc.Imgproc.RETR_EXTERNAL, org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE)
    
    var bestEllipse: RotatedRect? = null
    var maxArea = 0.0
    
    for (c in contours) {
        if (c.toArray().size >= 5) {
            val pt2f = org.opencv.core.MatOfPoint2f(*c.toArray())
            val ellipse = org.opencv.imgproc.Imgproc.fitEllipse(pt2f)
            val area = Math.PI * (ellipse.size.width / 2.0) * (ellipse.size.height / 2.0)
            if (area > maxArea && area > (gray.cols() * gray.rows() * 0.05)) {
                maxArea = area
                bestEllipse = ellipse
            }
        }
    }
    mat.release(); gray.release(); edges.release(); hierarchy.release()
    return bestEllipse
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
