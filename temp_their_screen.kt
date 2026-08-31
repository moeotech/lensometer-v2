package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.analysis.V4OpticalAnalyzer
import com.example.analysis.V4Result
import com.example.analysis.V4RunResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.sqrt

enum class V4Step {
    INIT,
    STEP_1_NO_LENS,
    STEP_2_WITH_LENS,
    ANALYZING,
    COMPLETE
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun V4ExperimentScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var currentStep by remember { mutableStateOf(V4Step.INIT) }
    var analysisErrorMessage by remember { mutableStateOf("") }
    var currentRunIndex by remember { mutableStateOf(0) }
    var retryCountForCurrentRun by remember { mutableStateOf(0) }
    
    val runResults = remember { mutableStateListOf<V4RunResult?>(null, null, null) }
    var overallResult by remember { mutableStateOf<V4Result?>(null) }
    
    val noLensFrames = remember { mutableStateListOf<Bitmap>() }
    val withLensFrames = remember { mutableStateListOf<Bitmap>() }
    
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var camera2ControlRef by remember { mutableStateOf<Camera2CameraControl?>(null) }
    var previewRef by remember { mutableStateOf<Preview?>(null) }
    
    var flashMode by remember { mutableStateOf("AUTO") }
    
    var frameCaptureCallback by remember { mutableStateOf<((ImageProxy) -> Unit)?>(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    
    var isProcessing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0f) }
    var alignMessage by remember { mutableStateOf("") }
    var cameraIdStr by remember { mutableStateOf("") }

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
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraControlRef = camera.cameraControl
                    camera2ControlRef = Camera2CameraControl.from(camera.cameraControl)
                    cameraIdStr = Camera2CameraInfo.from(camera.cameraInfo).cameraId
                } catch (exc: Exception) {}
            
        }, ContextCompat.getMainExecutor(context))
        
        onDispose {
            isDisposed = true
            if (cameraProviderFuture.isDone) {
                val provider = cameraProviderFuture.get()
                                imageAnalysisRef?.clearAnalyzer()
                if (previewRef != null) provider.unbind(previewRef)
                if (imageAnalysisRef != null) provider.unbind(imageAnalysisRef)

            }
            analysisExecutor.shutdown()
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    suspend fun captureFrames(targetList: MutableList<Bitmap>, lockAE: Boolean) {
        isProcessing = true
        targetList.clear()
        captureProgress = 0f
        
        if (lockAE) {
            camera2ControlRef?.let { c2c ->
                val builder = CaptureRequestOptions.Builder()
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                c2c.captureRequestOptions = builder.build()
            }
            delay(1000)
            
            // Lock
            camera2ControlRef?.let { c2c ->
                val builder = CaptureRequestOptions.Builder()
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                c2c.captureRequestOptions = builder.build()
            }
            delay(200)
        }
        
        var capturedCount = 0
        frameCaptureCallback = { imageProxy ->
            if (capturedCount < 30) {
                val bmp = v4ProxyToBitmap(imageProxy)
                if (bmp != null) {
                    targetList.add(bmp)
                    capturedCount++
                    captureProgress = capturedCount / 30f
                }
            }
        }
        
        while (capturedCount < 30 && isProcessing) {
            delay(50)
        }
        frameCaptureCallback = null
        if (!isProcessing) {
            throw kotlinx.coroutines.CancellationException("Capture cancelled")
        }
        isProcessing = false
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        if (currentStep == V4Step.STEP_2_WITH_LENS) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val radius = 100.dp.toPx()
                drawCircle(color = Color.Yellow, radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 4f))
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xBB000000))
                .padding(16.dp)
        ) {
            Text("V4 DIRECT LENS", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Camera ID: $cameraIdStr", color = Color.Gray, fontSize = 12.sp)
            
            if (currentStep == V4Step.STEP_2_WITH_LENS || currentStep == V4Step.ANALYZING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("RUN ${currentRunIndex + 1} OF 3", color = Color.Green, fontWeight = FontWeight.Bold)
                Text("Attempt ${retryCountForCurrentRun + 1} of 3", color = Color.LightGray)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isProcessing || currentStep == V4Step.ANALYZING) {
                Button(
                    onClick = {
                        frameCaptureCallback = null
                        isProcessing = false
                        currentStep = V4Step.INIT
                        currentRunIndex = 0
                        retryCountForCurrentRun = 0
                        withLensFrames.clear()
                        noLensFrames.clear()
                        runResults.clear(); runResults.add(null); runResults.add(null); runResults.add(null)
                        overallResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("STOP TEST", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (isProcessing) {
                LinearProgressIndicator(progress = { captureProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (analysisErrorMessage.isNotEmpty()) {
                Text(analysisErrorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            when (currentStep) {
                V4Step.INIT -> {
                    Button(onClick = { currentStep = V4Step.STEP_1_NO_LENS }, modifier = Modifier.fillMaxWidth()) {
                        Text("START EXPERIMENT")
                    }
                }
                V4Step.STEP_1_NO_LENS -> {
                    Text("STEP 1: REMOVE LENS", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text("Point camera at the A4 optical target.", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        coroutineScope.launch {
                            captureFrames(noLensFrames, lockAE = true)
                            currentStep = V4Step.STEP_2_WITH_LENS
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                        Text("CAPTURE REFERENCE")
                    }
                }
                V4Step.STEP_2_WITH_LENS -> {
                    Text("STEP 2: PLACE LENS DIRECTLY UNDER CAMERA", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text("Hold the spectacle lens flat and centered directly under the camera.", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        coroutineScope.launch {
                            captureFrames(withLensFrames, lockAE = false) // Keep locked state
                            currentStep = V4Step.ANALYZING
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                        Text("CAPTURE LENS")
                    }
                }
                V4Step.ANALYZING -> {
                    Text("ANALYZING...", color = Color.Cyan)
                    LaunchedEffect(Unit) {
                        coroutineScope.launch {
                            val result = V4OpticalAnalyzer.analyze(noLensFrames, withLensFrames)
                            
                            if (!result.success && retryCountForCurrentRun < 2) {
                                retryCountForCurrentRun++
                                analysisErrorMessage = "INSUFFICIENT OPTICAL FEATURES - HOLD STILL\n${result.errorMessage}"
                                // Retry same run
                                currentStep = V4Step.STEP_2_WITH_LENS
                                withLensFrames.clear()
                                camera2ControlRef?.let { c2c ->
                                    val builder = CaptureRequestOptions.Builder()
                                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
                                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    c2c.captureRequestOptions = builder.build()
                                }
                            } else {
                                analysisErrorMessage = ""
                                runResults[currentRunIndex] = result
                                retryCountForCurrentRun = 0
                                
                                if (currentRunIndex < 2) {
                                    currentRunIndex++
                                    currentStep = V4Step.STEP_2_WITH_LENS
                                    withLensFrames.clear()
                                    // Unlock AE for next run
                                    camera2ControlRef?.let { c2c ->
                                        val builder = CaptureRequestOptions.Builder()
                                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
                                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        c2c.captureRequestOptions = builder.build()
                                    }
                                } else {
                                    val validRuns = runResults.filterNotNull().filter { it.success }
                                    if (validRuns.size < 3) {
                                        overallResult = V4Result(
                                            success = true, // Force to true to show the FAIL UI properly
                                            measurementQualityPass = false,
                                            qualityMessage = "insufficient valid runs",
                                            sphDisplay = "N/A", cylDisplay = "N/A", axisDisplay = "N/A",
                                            lambda1 = 0.0, lambda2 = 0.0, isotropic = 0.0, anisotropic = 0.0,
                                            lambda1Std = 0.0, lambda2Std = 0.0, isotropicStd = 0.0, anisotropicStd = 0.0,
                                            trackedDots = 0, refDotCount = 0,
                                            commonGridPointsAcrossRuns = 0,
                                            correspondenceConsistency = 0.0,
                                            centerStdPx = 0.0,
                                            tensorStd = 0.0,
                                            registrationRms = 0.0,
                                            fieldFitRms = 0.0,
                                            allRuns = runResults.filterNotNull(),
                                            lastRunResult = runResults.lastOrNull(),
                                            errorMessage = ""
                                        )
                                    } else {
                                        overallResult = V4OpticalAnalyzer.calculateRepeatability(validRuns)
                                    }
                                    currentStep = V4Step.COMPLETE
                                }
                            }
                        }
                    }
                }
                V4Step.COMPLETE -> {
                    Text("MEASUREMENT COMPLETE", color = Color.Green, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        currentRunIndex = 0
                        runResults.clear(); runResults.add(null); runResults.add(null); runResults.add(null)
                        overallResult = null
                        currentStep = V4Step.INIT
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("RESTART")
                    }
                }
            }
        }
    }
    
    if (currentStep == V4Step.COMPLETE && overallResult != null) {
        V4ResultDialog(result = overallResult!!) {
            // Close dialog not needed, it sits on top. We can just wait for restart.
        }
    }
}

@Composable
fun V4ResultDialog(result: V4Result, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Text("V4 DIRECT LENS RESULT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (result.success) {
                if (result.measurementQualityPass) {
                    Text("MEASUREMENT QUALITY: PASS", color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("MEASUREMENT QUALITY: FAIL", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("REASON: ${result.qualityMessage}", color = Color.Yellow)
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("SPH: ${result.sphDisplay}", color = Color.Cyan, fontSize = 24.sp)
                Text("CYL: ${result.cylDisplay}", color = Color.Cyan, fontSize = 24.sp)
                Text("AXIS SIGNAL: ${result.axisDisplay}°", color = Color.Cyan, fontSize = 24.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("REGISTRATION DIAGNOSTICS", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Model: ${result.lastRunResult?.registrationModel ?: "NONE"}", color = Color.LightGray)
                Text("Scale: ${String.format("%.6f", result.lastRunResult?.registrationScale ?: 1.0)}", color = Color.LightGray)
                Text("Rot: ${String.format("%.2f", result.lastRunResult?.registrationRotationDeg ?: 0.0)}°", color = Color.LightGray)
                Text("Tx: ${String.format("%.1f", result.lastRunResult?.registrationTx ?: 0.0)}", color = Color.LightGray)
                Text("Ty: ${String.format("%.1f", result.lastRunResult?.registrationTy ?: 0.0)}", color = Color.LightGray)
                Text("Inliers: ${result.lastRunResult?.registrationInliers ?: 0} / ${result.lastRunResult?.registrationFeatureCount ?: 0}", color = Color.LightGray)

                
                Spacer(modifier = Modifier.height(16.dp))
                Text("RAW OPTICAL FIELD", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Lambda 1: ${String.format("%.6f", result.lambda1)}", color = Color.LightGray)
                Text("Lambda 2: ${String.format("%.6f", result.lambda2)}", color = Color.LightGray)
                Text("Isotropic: ${String.format("%.6f", result.isotropic)}", color = Color.LightGray)
                Text("Anisotropic: ${String.format("%.6f", result.anisotropic)}", color = Color.LightGray)
                Text("Axis: ${result.axisDisplay}°", color = Color.LightGray)
                Text("Matched dots: ${result.trackedDots}", color = Color.LightGray)
                Text("Common Grid Points: ${result.commonGridPointsAcrossRuns}", color = Color.LightGray)
                Text("Correspondence Consistency: ${String.format("%.1f", result.correspondenceConsistency * 100.0)}%", color = Color.LightGray)
                Text("Center StdPx: ${String.format("%.2f", result.centerStdPx)}", color = Color.LightGray)
                Text("Tensor Std: ${String.format("%.6f", result.tensorStd)}", color = Color.LightGray)
                Text("Stable dots: ${result.refDotCount}", color = Color.LightGray)
                
                val framesAcc = result.allRuns.sumOf { it.framesAccepted }
                Text("Frames accepted: $framesAcc", color = Color.LightGray)
                Text("Registration RMS: ${String.format("%.3f", result.registrationRms)}", color = Color.LightGray)
                Text("Field-fit RMS: ${String.format("%.3f", result.fieldFitRms)}", color = Color.LightGray)

                Spacer(modifier = Modifier.height(16.dp))
                Text("MEAN / STD DEV (REPEATABILITY)", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Lambda 1 StdDev: ${String.format("%.6f", result.lambda1Std)}", color = Color.LightGray)
                Text("Lambda 2 StdDev: ${String.format("%.6f", result.lambda2Std)}", color = Color.LightGray)
                Text("Isotropic StdDev: ${String.format("%.6f", result.isotropicStd)}", color = Color.LightGray)
                Text("Anisotropic StdDev: ${String.format("%.6f", result.anisotropicStd)}", color = Color.LightGray)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("INDIVIDUAL RUNS", color = Color.Yellow, fontWeight = FontWeight.Bold)
                result.allRuns.forEachIndexed { index, run ->
                    Text("RUN ${index + 1}:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("L1: ${String.format("%.4f", run.lambda1)} L2: ${String.format("%.4f", run.lambda2)} Iso: ${String.format("%.4f", run.isotropic)}", color = Color.LightGray)
                    
                    Text("TOPOLOGY TELEMETRY", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Ref Dots: ${run.refDotCount} | Lens Dots: ${run.lensDotCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Grid Assigned: ${run.matchRejections["gridAssigned"] ?: 0} | Grid Ambiguous: ${run.matchRejections["gridAmbiguous"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Seed Mutual Matches: ${run.matchRejections["seedMutualMatches"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Neighbor-expanded: ${run.matchRejections["neighborExpandedMatches"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Affine-expanded: ${run.matchRejections["affineExpandedMatches"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Final 1-to-1 matches: ${run.matchRejections["finalMatches"] ?: run.topologyMatchCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Quadrants: ${listOf(run.matchRejections["Quad1_Matches"] ?: 0, run.matchRejections["Quad2_Matches"] ?: 0, run.matchRejections["Quad3_Matches"] ?: 0, run.matchRejections["Quad4_Matches"] ?: 0).count { it > 0 }}", color = Color.LightGray, fontSize = 12.sp)
                    
                    val coverageStr = if (run.refDotCount > 0) String.format("%.1f", ((run.matchRejections["finalMatches"] ?: run.topologyMatchCount).toDouble() / run.refDotCount) * 100) + "%" else "0%"
                    Text("Spatial Coverage: $coverageStr", color = Color.LightGray, fontSize = 12.sp)
                    
                    Text("REJECTIONS:", color = Color.LightGray, fontSize = 12.sp)
                    Text("seed_distance: ${run.matchRejections["seed_distance"] ?: 0} | non_mutual: ${run.matchRejections["non_mutual"] ?: 0}", color = Color.Red, fontSize = 12.sp)
                    Text("topology_collision: ${run.matchRejections["gridCollisions"] ?: 0}", color = Color.Red, fontSize = 12.sp)
                    Text("assignment_conflict: ${run.matchRejections["assignment_conflict"] ?: 0}", color = Color.Red, fontSize = 12.sp)
                    Text("unmatched_lens: ${run.matchRejections["unmatched_lens_dots"] ?: 0} | unmatched_ref: ${run.matchRejections["topology_rejection"] ?: 0}", color = Color.Red, fontSize = 12.sp)
                    
                    Text("Q1: ${run.matchRejections["Quad1_Matches"] ?: 0} Q2: ${run.matchRejections["Quad2_Matches"] ?: 0} Q3: ${run.matchRejections["Quad3_Matches"] ?: 0} Q4: ${run.matchRejections["Quad4_Matches"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    
                    Text("REGISTRATION", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Features: ${run.registrationFeatureCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Inliers: ${run.registrationInliers}", color = Color.LightGray, fontSize = 12.sp)
                    Text("RMS: ${String.format("%.3f", run.registrationRms)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Model: ${run.registrationModel}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Scale: ${String.format("%.4f", run.registrationScale)}", color = Color.LightGray, fontSize = 12.sp)
                    
                    Text("TOPOLOGY", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Input: ${run.matchRejections["topologyInputDots"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Assigned: ${run.matchRejections["topologyAssignedDots"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Unassigned: ${run.matchRejections["topologyUnassignedDots"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Collisions: ${run.matchRejections["topologyCollisions"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Consistency errs: ${run.matchRejections["topologyConsistencyErrors"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)

                    Text("OPTICAL CENTER", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Valid: ${run.opticalCenterValid}", color = if (run.opticalCenterValid) Color.Green else Color.Red, fontSize = 12.sp)
                    Text("Cond num: ${String.format("%.2f", run.opticalCenterConditionNumber)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Confidence: ${String.format("%.3f", run.opticalCenterConfidence)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Center: (${String.format("%.1f", run.opticalCenterX)}, ${String.format("%.1f", run.opticalCenterY)})", color = Color.LightGray, fontSize = 12.sp)

                    Text("OPTICAL FIELD", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Input points: ${run.opticalFieldInputCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Retained points: ${run.opticalFieldRetainedCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Local outliers: ${run.localOutlierRejections}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Crossing rejected: ${run.crossingVectorRejections}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Median local res: ${String.format("%.3f", run.medianLocalResidual)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("MAD local res: ${String.format("%.3f", run.madLocalResidual)}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Raw Disp Median: ${String.format("%.2f", run.dispMedian)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Raw Disp MAD: ${String.format("%.2f", run.dispMAD)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Raw Disp P90: ${String.format("%.2f", run.dispP90)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Raw Disp Max: ${String.format("%.2f", run.dispMax)}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Global Motion X: ${String.format("%.2f", run.globalMotionX)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Global Motion Y: ${String.format("%.2f", run.globalMotionY)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Global Motion Mag: ${String.format("%.2f", run.globalMotionMagnitude)}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Corrected Disp Median: ${String.format("%.2f", run.correctedDispMedian)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Corrected Disp MAD: ${String.format("%.2f", run.correctedDispMAD)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Corrected Disp P90: ${String.format("%.2f", run.correctedDispP90)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Corrected Disp Max: ${String.format("%.2f", run.correctedDispMax)}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Optical Center: ${String.format("%.1f", run.opticalCenterX)}, ${String.format("%.1f", run.opticalCenterY)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Robust Inliers: ${run.robustInliersCount} / ${run.pairs.count { it.status == "RETAINED" }}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Weighted Fit RMS: ${String.format("%.3f", run.weightedFitRms)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Antisymmetric Mag: ${String.format("%.4f", run.antisymmetricMag)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Axis Confidence: ${String.format("%.2f", run.axisConfidence)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Tensor A: [${String.format("%.4f", run.tensorA11)}, ${String.format("%.4f", run.tensorA12)}]", color = Color.LightGray, fontSize = 12.sp)
                    Text("          [${String.format("%.4f", run.tensorA21)}, ${String.format("%.4f", run.tensorA22)}]", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Quadrants: ${run.quadrantCoverage}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Coverage: ${String.format("%.1f", run.spatialCoveragePct)}%", color = Color.LightGray, fontSize = 12.sp)
                    Text("Fit RMS: ${String.format("%.3f", run.fieldFitRms)}", color = Color.LightGray, fontSize = 12.sp)
                    
                    if (run.matchRejections.isNotEmpty()) {
                        Text("Other rejections: ${run.matchRejections.entries.filter { !it.key.startsWith("Quad") }.joinToString { "${it.key}: ${it.value}" }}", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!run.success) {
                        Text("FAILURE: ${run.errorMessage}", color = Color.Red, fontSize = 12.sp)
                    }
                }
                
                if (result.visualVectorMap != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("VISUAL VECTOR MAP (DISPLAY ONLY)", color = Color.White)
                    var mag by remember { mutableStateOf(10f) }
                    var useCorrectedVectors by remember { mutableStateOf(true) }
                    Row {
                        Button(onClick = { mag = 1f }) { Text("1x") }
                        Button(onClick = { mag = 5f }) { Text("5x") }
                        Button(onClick = { mag = 10f }) { Text("10x") }
                        Button(onClick = { mag = 20f }) { Text("20x") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Motion Corrected Vectors:", color = Color.White)
                        Switch(checked = useCorrectedVectors, onCheckedChange = { useCorrectedVectors = it })
                    }
                    val bmp = V4OpticalAnalyzer.drawVectorMap(result, mag, useCorrectedVectors)
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Vector Map",
                            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                        )
                    }
                }
            } else {
                Text("MEASUREMENT QUALITY: FAIL", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Reason: ${result.errorMessage}", color = Color.Yellow)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

fun v4ProxyToBitmap(proxy: ImageProxy): Bitmap? {
    val yBuffer = proxy.planes[0].buffer
    val uBuffer = proxy.planes[1].buffer
    val vBuffer = proxy.planes[2].buffer
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, proxy.width, proxy.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, proxy.width, proxy.height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
