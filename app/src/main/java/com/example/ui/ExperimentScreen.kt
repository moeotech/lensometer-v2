package com.example.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.analysis.OpticalAnalyzer
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

enum class ExperimentStep {
    STEP_1_NO_LENS_1,
    STEP_2_NO_LENS_2,
    STEP_3_LENS_1,
    STEP_4_LENS_2,
    STEP_5_ANALYSIS,
    COMPLETE
}

enum class CaptureSubPhase {
    IDLE,
    CAPTURING,
    DONE
}

@Composable
fun ExperimentScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }


    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var currentStep by remember { mutableStateOf(ExperimentStep.STEP_1_NO_LENS_1) }
    var subPhase by remember { mutableStateOf(CaptureSubPhase.IDLE) }
    var flashAvailable by remember { mutableStateOf(true) }
    var torchEnabled by remember { mutableStateOf(false) }

    val noLens1Frames = remember { mutableStateListOf<FrameMeasurement>() }
    val noLens2Frames = remember { mutableStateListOf<FrameMeasurement>() }
    val lens1Frames = remember { mutableStateListOf<FrameMeasurement>() }
    val lens2Frames = remember { mutableStateListOf<FrameMeasurement>() }

    var liveAvgLum by remember { mutableStateOf(0f) }
    var liveSharpness by remember { mutableStateOf(0f) }
    var liveBrightPx by remember { mutableStateOf(0f) }
    var liveReflections = remember { mutableStateOf<List<ReflectionCandidate>>(emptyList()) }

    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }

    var flashMode by remember { mutableStateOf("AUTO") }
    LaunchedEffect(flashMode, cameraControlRef) {
        try {
            when (flashMode) {
                "ON" -> cameraControlRef?.enableTorch(true)
                "OFF" -> cameraControlRef?.enableTorch(false)
                "AUTO" -> {
                    cameraControlRef?.enableTorch(false)
                }
            }
        } catch (e: Exception) {}
    }

    var imageAnalysisRef by remember { mutableStateOf<ImageAnalysis?>(null) }
    var previewRef by remember { mutableStateOf<Preview?>(null) }

    var experimentReport by remember { mutableStateOf<ExperimentReport?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var frameCaptureCallback by remember { mutableStateOf<((ImageProxy) -> Unit)?>(null) }

    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycleOwner) {
        var isDisposed = false
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var imageAnalysisRef: ImageAnalysis? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)


                cameraProviderFuture.addListener({
                    if (isDisposed) return@addListener

                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        previewRef = preview
                    
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    try {
                                        frameCaptureCallback?.invoke(imageProxy)
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }
                        imageAnalysisRef = imageAnalysis
                    
                        try {
                                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControlRef = camera.cameraControl
                            flashAvailable = camera.cameraInfo.hasFlashUnit()
                        } catch (exc: Exception) {
                            flashAvailable = false
                        }
                
                    
                }, ContextCompat.getMainExecutor(context))
        
        onDispose {
            isDisposed = true
            if (cameraProviderFuture.isDone) {
                val provider = cameraProviderFuture.get()
                                imageAnalysisRef?.clearAnalyzer()
                                provider.unbindAll()
                
                

            }
            analysisExecutor.shutdown()
        }
    }
    suspend fun runCaptureSequence(phaseName: String, targetList: androidx.compose.runtime.snapshots.SnapshotStateList<FrameMeasurement>) {
        isProcessing = true
        targetList.clear()

        subPhase = CaptureSubPhase.CAPTURING
        
        // Lock AE/AWB/AF by disabling auto
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val action = FocusMeteringAction.Builder(
            factory.createPoint(0.5f, 0.5f),
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        ).disableAutoCancel().build()
        try {
            cameraControlRef?.startFocusAndMetering(action)
        } catch (e: Exception) {}

        if (flashAvailable) {
            torchEnabled = true
            try {
                cameraControlRef?.enableTorch(true)
            } catch (e: Exception) {}
        }
        delay(600) // let exposure settle

        val capturedFrames = mutableListOf<FrameMeasurement>()
        var attempts = 0
        while (capturedFrames.size < 10 && attempts < 100) {
            attempts++
            var captured: FrameMeasurement? = null
            frameCaptureCallback = { proxy ->
                if (captured == null) {
                    try {
                        captured = OpticalAnalyzer.analyzeFrame(proxy, capturedFrames.size + 1, true, phaseName.contains("LENS"))
                    } catch (e: Exception) {
                        captured = null
                    }
                }
            }
            delay(100)
            captured?.let {
                capturedFrames.add(it)
                saveProxyImage(context, prefix = "${phaseName}_frame", index = capturedFrames.size)
            }
        }
        targetList.addAll(capturedFrames)

        torchEnabled = false
        try {
            cameraControlRef?.enableTorch(false)
        } catch (e: Exception) {}
        
        frameCaptureCallback = null
        subPhase = CaptureSubPhase.DONE
        isProcessing = false
    }

    val bgColor = Color(0xFF1C1B1F)
    val textColor = Color(0xFFE6E1E5)
    val primaryColor = Color(0xFFD0BCFF)
    val cardBg = Color(0xFF2B2930)
    val borderColor = Color(0xFF49454F)
    val accentBg = Color(0xFF381E72)
    val accentText = Color(0xFFD0BCFF)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "SCIENTIFIC PROTOTYPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 2.sp)
                    Text(text = "OPTICAL EXPERIMENT V1.1", fontSize = 18.sp, fontWeight = FontWeight.Light, color = textColor, letterSpacing = (-0.5).sp)
                }
                Surface(
                    color = Color(0xFF49454F),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, primaryColor)
                ) {
                    Text(text = "V1.1.0", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentStep != ExperimentStep.COMPLETE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1B1B1F))
                        .border(4.dp, Color.White, RoundedCornerShape(24.dp))
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(160.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    }

                    if (subPhase == CaptureSubPhase.CAPTURING) {
                        val currentListSize = when (currentStep) {
                            ExperimentStep.STEP_1_NO_LENS_1 -> noLens1Frames.size
                            ExperimentStep.STEP_2_NO_LENS_2 -> noLens2Frames.size
                            ExperimentStep.STEP_3_LENS_1 -> lens1Frames.size
                            ExperimentStep.STEP_4_LENS_2 -> lens2Frames.size
                            else -> 0
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Capturing Frames...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "$currentListSize / 10", color = primaryColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { currentListSize / 10f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = primaryColor,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MetricCell("Avg Lum", String.format("%.1f", liveAvgLum))
                            MetricCell("Sharpness", String.format("%.1f", liveSharpness))
                            MetricCell("Bright Px", String.format("%.1f%%", liveBrightPx))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (!isProcessing) {
                            coroutineScope.launch {
                                when (currentStep) {
                                    ExperimentStep.STEP_1_NO_LENS_1 -> {
                                        runCaptureSequence("NO_LENS_1", noLens1Frames)
                                        currentStep = ExperimentStep.STEP_2_NO_LENS_2
                                        Toast.makeText(context, "No Lens 1 Done. Keep device steady for No Lens 2.", Toast.LENGTH_SHORT).show()
                                    }
                                    ExperimentStep.STEP_2_NO_LENS_2 -> {
                                        runCaptureSequence("NO_LENS_2", noLens2Frames)
                                        currentStep = ExperimentStep.STEP_3_LENS_1
                                        Toast.makeText(context, "No Lens 2 Done. Place Lens now.", Toast.LENGTH_LONG).show()
                                    }
                                    ExperimentStep.STEP_3_LENS_1 -> {
                                        runCaptureSequence("LENS_1", lens1Frames)
                                        currentStep = ExperimentStep.STEP_4_LENS_2
                                        Toast.makeText(context, "Lens 1 Done. Keep device steady for Lens 2.", Toast.LENGTH_SHORT).show()
                                    }
                                    ExperimentStep.STEP_4_LENS_2 -> {
                                        runCaptureSequence("LENS_2", lens2Frames)
                                        currentStep = ExperimentStep.STEP_5_ANALYSIS
                                        Toast.makeText(context, "Capture Complete. Ready for Analysis.", Toast.LENGTH_SHORT).show()
                                    }
                                    ExperimentStep.STEP_5_ANALYSIS -> {
                                        isProcessing = true
                                        val p1 = OpticalAnalyzer.summarizePhase("NO_LENS_1", noLens1Frames)
                                        val p2 = OpticalAnalyzer.summarizePhase("NO_LENS_2", noLens2Frames)
                                        val p3 = OpticalAnalyzer.summarizePhase("LENS_1", lens1Frames)
                                        val p4 = OpticalAnalyzer.summarizePhase("LENS_2", lens2Frames)
                                        
                                        val report = OpticalAnalyzer.comparePhases(p1, p2, p3, p4)
                                        experimentReport = report
                                        saveReportToJson(context, report)
                                        isProcessing = false
                                        currentStep = ExperimentStep.COMPLETE
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color(0xFF381E72)),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "PROCESSING...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        val btnText = when (currentStep) {
                            ExperimentStep.STEP_1_NO_LENS_1 -> "START: NO LENS TEST 1"
                            ExperimentStep.STEP_2_NO_LENS_2 -> "START: NO LENS TEST 2"
                            ExperimentStep.STEP_3_LENS_1 -> "START: SAME LENS TEST 1"
                            ExperimentStep.STEP_4_LENS_2 -> "START: SAME LENS TEST 2"
                            ExperimentStep.STEP_5_ANALYSIS -> "RUN OPTICAL ANALYSIS"
                            else -> "COMPLETE"
                        }
                        Text(text = btnText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

            } else {
                experimentReport?.let { report ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .background(cardBg, RoundedCornerShape(24.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Text(text = "REPEATABILITY TEST", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        Spacer(modifier = Modifier.height(12.dp))

                        val nlMean = (report.noLens1.meanLuminance + report.noLens2.meanLuminance) / 2f
                        val nlStd = (report.noLens1.stdDevLuminance + report.noLens2.stdDevLuminance) / 2f
                        val lMean = (report.lens1.meanLuminance + report.lens2.meanLuminance) / 2f
                        val lStd = (report.lens1.stdDevLuminance + report.lens2.stdDevLuminance) / 2f

                        ResultSectionTitle("NO LENS:")
                        ResultRow("Luminance", String.format("%.2f ± %.2f", nlMean, nlStd))

                        Spacer(modifier = Modifier.height(12.dp))
                        ResultSectionTitle("WITH LENS:")
                        ResultRow("Luminance", String.format("%.2f ± %.2f", lMean, lStd))

                        Spacer(modifier = Modifier.height(12.dp))
                        ResultSectionTitle("OPTICAL DIFFERENCE:")
                        ResultRow("Status", if (report.opticalDifferenceDetected) "detected" else "not detected")
                        ResultRow("CONFIDENCE:", "experimental only")

                        Spacer(modifier = Modifier.height(16.dp))

                        MetricGraph(
                            title = "Luminance Progression (40 frames)",
                            data1 = report.noLens1.frames.map { it.avgLuminance },
                            data2 = report.noLens2.frames.map { it.avgLuminance },
                            data3 = report.lens1.frames.map { it.avgLuminance },
                            data4 = report.lens2.frames.map { it.avgLuminance },
                            color1 = Color(0xFFD0BCFF),
                            color2 = Color(0xFF9A82DB),
                            color3 = Color(0xFF4DB6AC),
                            color4 = Color(0xFF26A69A)
                        )
                        
                        MetricGraph(
                            title = "Sharpness Progression",
                            data1 = report.noLens1.frames.map { it.sharpness },
                            data2 = report.noLens2.frames.map { it.sharpness },
                            data3 = report.lens1.frames.map { it.sharpness },
                            data4 = report.lens2.frames.map { it.sharpness },
                            color1 = Color(0xFFFFB4AB),
                            color2 = Color(0xFFFF897D),
                            color3 = Color(0xFFFFD8E4),
                            color4 = Color(0xFFF2B8B5)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                currentStep = ExperimentStep.STEP_1_NO_LENS_1
                                noLens1Frames.clear()
                                noLens2Frames.clear()
                                lens1Frames.clear()
                                lens2Frames.clear()
                                experimentReport = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color(0xFF381E72))
                        ) {
                            Text(text = "RESTART EXPERIMENT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricGraph(
    title: String,
    data1: List<Float>,
    data2: List<Float>,
    data3: List<Float>,
    data4: List<Float>,
    color1: Color,
    color2: Color,
    color3: Color,
    color4: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1C1B1F), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))) {
            val allData = data1 + data2 + data3 + data4
            if (allData.isEmpty()) return@Canvas
            
            val maxVal = allData.maxOrNull() ?: 1f
            val minVal = allData.minOrNull() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(0.1f)
            
            val stepX = size.width / 9f // 10 points -> 9 intervals

            fun drawLineGraph(data: List<Float>, color: Color) {
                if (data.size < 2) return
                val path = Path()
                data.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height - ((value - minVal) / range) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            drawLineGraph(data1, color1)
            drawLineGraph(data2, color2)
            drawLineGraph(data3, color3)
            drawLineGraph(data4, color4)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NL1", color = color1, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("NL2", color = color2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("L1", color = color3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("L2", color = color4, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MetricCell(label: String, value: String) {
    Column {
        Text(text = label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFCAC4D0))
        Text(text = value, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
    }
}

@Composable
fun ResultSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFFD0BCFF),
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFFCAC4D0))
        Text(text = value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
    }
}

fun saveProxyImage(context: Context, prefix: String, index: Int) {
    try {
        val file = File(context.filesDir, "${prefix}_$index.dat")
        FileOutputStream(file).use { fos ->
            fos.write(prefix.toByteArray())
        }
    } catch (e: Exception) {}
}

fun saveReportToJson(context: Context, report: ExperimentReport) {
    try {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val jsonAdapter = moshi.adapter(ExperimentReport::class.java)
        val json = jsonAdapter.toJson(report)
        val file = File(context.filesDir, "optical_experiment_report.json")
        FileOutputStream(file).use { fos ->
            fos.write(json.toByteArray())
        }
    } catch (e: Exception) {}
}
