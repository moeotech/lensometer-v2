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
import com.example.analysis.v5.V5DeflectometryAnalyzer
import com.example.analysis.v5.V5MatchResult
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
                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
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
                                provider.unbindAll()
                
                

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
                }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isProcessing || currentStep == V4Step.ANALYZING) {
                Button(
                    onClick = {
                        frameCaptureCallback = null
                        isProcessing = false
                        currentStep = V4Step.INIT
                        currentRunIndex = 0
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
                            analysisErrorMessage = if (!result.success) "Run failed: ${result.errorMessage}" else ""
                            runResults[currentRunIndex] = result
                            if (currentRunIndex < 2) {
                                currentRunIndex++
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
                                overallResult = V4OpticalAnalyzer.calculateRepeatability(runResults.filterNotNull())
                                currentStep = V4Step.COMPLETE
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
        V4ResultDialog(result = overallResult!!, noLensFrames = noLensFrames, withLensFrames = withLensFrames) {
            // Close dialog not needed, it sits on top. We can just wait for restart.
        }
    }
}

@Composable
fun V4ResultDialog(result: V4Result, noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>, onDismiss: () -> Unit) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var v5Result by remember { mutableStateOf<com.example.analysis.v5.V5MatchResult?>(null) }
    var isV5Analyzing by remember { mutableStateOf(true) }
    
    var v6Result by remember { mutableStateOf<com.example.analysis.v6.V6Result?>(null) }
    var isV6Analyzing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val res = V5DeflectometryAnalyzer.analyze(noLensFrames, withLensFrames)
            v5Result = res
            isV5Analyzing = false
            
            val res6 = com.example.analysis.v6.V6StructuredDeflectometryAnalyzer.analyze(noLensFrames, withLensFrames)
            v6Result = res6
            isV6Analyzing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Text("V4 vs V5 COMPARATIVE EXPERIMENT RESULT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Button(onClick = { onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("CLOSE")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // --- V5 SECTION ---
            Text("--- V5 GEOMETRIC CORRESPONDENCE ENGINE ---", color = Color.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (isV5Analyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Running V5 geometric correspondence...", color = Color.Cyan)
            } else if (v5Result != null) {
                val v5 = v5Result!!
                val tel = v5.telemetry
                Text(
                    text = "V5 Status: ${if (v5.success) "SUCCESS" else "FAILED"} (Corr: ${tel.correspondenceSuccess}, Quality: ${tel.measurementQualityValid})",
                    color = if (v5.success) Color.Green else Color.Red
                )
                Text("Reference Points: ${tel.referencePointCount} | Lens Points: ${tel.lensPointCount}", color = Color.LightGray)
                Text("Initial Candidate Matches: ${tel.initialCandidateMatches} | Seed Matches: ${tel.seedMatchCount}", color = Color.LightGray)
                Text("Final Accepted Inlier Matches: ${tel.acceptedInlierMatches}", color = Color.Green, fontWeight = FontWeight.Bold)
                Text("Residuals — Median: ${String.format("%.2f", tel.medianResidualPx)}px | MAD: ${String.format("%.2f", tel.residualMadPx)}px | Max: ${String.format("%.2f", tel.maxAcceptedResidualPx)}px", color = Color.Cyan)
                Text("Quadrants Covered: ${tel.quadrantsCovered} (Q1: ${tel.q1Matches}, Q2: ${tel.q2Matches}, Q3: ${tel.q3Matches}, Q4: ${tel.q4Matches})", color = Color.LightGray)
                Text("Spatial Coverage: ${String.format("%.1f", tel.coveragePct)}%", color = Color.LightGray)
                Text("Prediction Transform: Tx=${String.format("%.1f", tel.predictionTx)}, Ty=${String.format("%.1f", tel.predictionTy)}, Rot=${String.format("%.1f", tel.predictionRotationDeg)}°, Scale=${String.format("%.3f", tel.predictionScale)}, RMS=${String.format("%.2f", tel.transformRms)}px", color = Color.LightGray)

                if (v5.rawFieldResult != null) {
                    val rf = v5.rawFieldResult!!
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("RAW FIELD / DEFORMATION (RAW / UNCALIBRATED):", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text("Mean Dx: ${String.format("%.2f", rf.meanDx)} | Mean Dy: ${String.format("%.2f", rf.meanDy)}", color = Color.LightGray)
                    Text("Principal Values: λ1=${String.format("%.4f", rf.principalValue1)}, λ2=${String.format("%.4f", rf.principalValue2)}", color = Color.Cyan)
                    Text("Principal Angle: ${String.format("%.1f°", rf.principalAngleDeg)}", color = Color.Cyan)
                    Text("Isotropic Component: ${String.format("%.4f", rf.isotropicComponent)}", color = Color.LightGray)
                    Text("Anisotropic Component: ${String.format("%.4f", rf.anisotropicComponent)}", color = Color.LightGray)
                }

                if (v5.debugBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("V5 DEBUG CORRESPONDENCE VISUALIZATION", color = Color.White, fontSize = 14.sp)
                    androidx.compose.foundation.Image(
                        bitmap = v5.debugBitmap!!.asImageBitmap(),
                        contentDescription = "V5 Debug View",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val v5Export = buildString {
                        appendLine("=== V5 GEOMETRIC CORRESPONDENCE EXPORT ===")
                        appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                        appendLine("Success: ${v5.success} (Correspondence Success: ${tel.correspondenceSuccess}, Quality Valid: ${tel.measurementQualityValid})")
                        appendLine("Reference Points: ${tel.referencePointCount}")
                        appendLine("Lens Points: ${tel.lensPointCount}")
                        appendLine("Ref Median Spacing: ${tel.referenceMedianSpacing}")
                        appendLine("Lens Median Spacing: ${tel.lensMedianSpacing}")
                        appendLine("Initial Candidate Matches: ${tel.initialCandidateMatches}")
                        appendLine("Seed Matches: ${tel.seedMatchCount}")
                        appendLine("Pre-validation Matches: ${tel.preValidationMatches}")
                        appendLine("Accepted Inlier Matches: ${tel.acceptedInlierMatches}")
                        appendLine("Rejected Residual: ${tel.rejectedResidual}")
                        appendLine("Rejected Local Consistency: ${tel.rejectedLocalConsistency}")
                        appendLine("Rejected Collision: ${tel.rejectedCollision}")
                        appendLine("Rejected Transform: ${tel.rejectedTransform}")
                        appendLine("Median Residual Px: ${tel.medianResidualPx}")
                        appendLine("Residual MAD Px: ${tel.residualMadPx}")
                        appendLine("Max Accepted Residual Px: ${tel.maxAcceptedResidualPx}")
                        appendLine("Transform Inliers: ${tel.transformInliers}")
                        appendLine("Transform RMS: ${tel.transformRms}")
                        appendLine("Iterations: ${tel.iterationCount}")
                        appendLine("Matches Added Per Iteration: ${tel.matchesAddedPerIteration}")
                        appendLine("Q1: ${tel.q1Matches} | Q2: ${tel.q2Matches} | Q3: ${tel.q3Matches} | Q4: ${tel.q4Matches}")
                        appendLine("Quadrants Covered: ${tel.quadrantsCovered}")
                        appendLine("Coverage Pct: ${tel.coveragePct}%")
                        appendLine("Prediction Tx: ${tel.predictionTx}")
                        appendLine("Prediction Ty: ${tel.predictionTy}")
                        appendLine("Prediction Rotation: ${tel.predictionRotationDeg}")
                        appendLine("Prediction Scale: ${tel.predictionScale}")
                        appendLine("")
                        appendLine("--- ACCEPTED CORRESPONDENCES ---")
                        for (corr in v5.correspondences) {
                            appendLine("Ref[${corr.referenceIndex}] (${corr.referencePoint.x}, ${corr.referencePoint.y}) -> Lens[${corr.lensIndex}] (${corr.observedPoint.x}, ${corr.observedPoint.y}) [Pred: (${corr.predictedPoint.x}, ${corr.predictedPoint.y}), Res: ${corr.residualPx}, Dx: ${corr.rawDx}, Dy: ${corr.rawDy}, Inlier: ${corr.isInlier}]")
                        }
                        v5.rawFieldResult?.let { rf ->
                            appendLine("")
                            appendLine("--- RAW FIELD ---")
                            appendLine("Mean Dx: ${rf.meanDx} | Mean Dy: ${rf.meanDy}")
                            appendLine("Principal Value 1: ${rf.principalValue1}")
                            appendLine("Principal Value 2: ${rf.principalValue2}")
                            appendLine("Principal Angle: ${rf.principalAngleDeg}")
                            appendLine("Isotropic: ${rf.isotropicComponent}")
                            appendLine("Anisotropic: ${rf.anisotropicComponent}")
                        }
                    }
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(v5Export))
                    android.widget.Toast.makeText(context, "V5 Test Data Copied to Clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                    Text("EXPORT V5 TEST DATA", color = Color.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // --- V6 SECTION ---
            Text("--- V6 STRUCTURED DEFLECTOMETRY ---", color = Color.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (isV6Analyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Running V6 structured deflectometry...", color = Color.Cyan)
            } else if (v6Result != null) {
                val v6 = v6Result!!
                val tel = v6.telemetry
                Text(
                    text = "V6 Status: ${if (tel.success) "SUCCESS" else "FAILED"} (${tel.failureReason})",
                    color = if (tel.success) Color.Green else Color.Red
                )
                Text("Reference Valid Cells: ${tel.referenceValidCells} | Lens Valid Cells: ${tel.lensValidCells}", color = Color.LightGray)
                Text("Valid Directional Vectors: ${tel.validDirectionalVectors}", color = Color.Green, fontWeight = FontWeight.Bold)
                Text("Ratio Median: ${String.format("%.4f", tel.ratioMedian)} | Ratio Range: ${String.format("%.4f", tel.ratioRange)}", color = Color.Cyan)
                Text("Directional Consistency: ${tel.directionalConsistency}", color = Color.LightGray)
                
                if (v6.fitResult != null) {
                    val f = v6.fitResult!!
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SINUSOIDAL FITTING (RAW RATIOS):", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text("A0 (Mean Ratio): ${String.format("%.4f", f.a0)}", color = Color.Cyan)
                    Text("ACos: ${String.format("%.4f", f.aCos)} | ASin: ${String.format("%.4f", f.aSin)}", color = Color.LightGray)
                    Text("Astigmatic Amplitude: ${String.format("%.4f", f.astigmaticAmplitude)}", color = Color.LightGray)
                    Text("Principal Orientation: ${String.format("%.1f°", f.principalOrientation)}", color = Color.Cyan)
                }

                if (v6.debugBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("V6 DEBUG VISUALIZATION", color = Color.White, fontSize = 14.sp)
                    androidx.compose.foundation.Image(
                        bitmap = v6.debugBitmap!!.asImageBitmap(),
                        contentDescription = "V6 Debug View",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val v6Export = buildString {
                        appendLine("=== V6 STRUCTURED DEFLECTOMETRY EXPORT ===")
                        appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                        appendLine("Git Commit: ${tel.gitCommit}")
                        appendLine("Success: ${tel.success} (${tel.failureReason})")
                        appendLine("Reference Valid Cells: ${tel.referenceValidCells}")
                        appendLine("Lens Valid Cells: ${tel.lensValidCells}")
                        appendLine("Valid Directional Vectors: ${tel.validDirectionalVectors}")
                        appendLine("Ratio Median: ${tel.ratioMedian}")
                        appendLine("Ratio Range: ${tel.ratioRange}")
                        appendLine("Directional Consistency: ${tel.directionalConsistency}")
                        appendLine("")
                        if (v6.fitResult != null) {
                            val f = v6.fitResult!!
                            appendLine("--- RAW SINUSOIDAL FIT ---")
                            appendLine("A0 (Mean Ratio): ${f.a0}")
                            appendLine("ACos: ${f.aCos}")
                            appendLine("ASin: ${f.aSin}")
                            appendLine("Amplitude: ${f.astigmaticAmplitude}")
                            appendLine("Orientation: ${f.principalOrientation}")
                            appendLine("")
                        }
                        appendLine("--- DEVICE GEOMETRY ---")
                        appendLine("CameraToLensMm: ${tel.deviceGeometry.cameraToLensDistanceMm}")
                        appendLine("LensToTargetMm: ${tel.deviceGeometry.lensToTargetDistanceMm}")
                        appendLine("TargetDotSpacingMm: ${tel.deviceGeometry.targetDotSpacingMm}")
                        appendLine("TargetVersion: ${tel.deviceGeometry.targetVersion}")
                        appendLine("")
                        appendLine("--- RAW DIRECTIONAL MEASUREMENTS ---")
                        for (m in v6.measurements) {
                            appendLine("Angle: ${String.format("%.1f", m.angleDegrees)}, RefR: ${String.format("%.2f", m.radiusReference)}, LensR: ${String.format("%.2f", m.radiusLens)}, Ratio: ${String.format("%.4f", m.radiusRatio)}")
                        }
                    }
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(v6Export))
                    android.widget.Toast.makeText(context, "V6 Test Data Copied to Clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)) {
                    Text("EXPORT V6 TEST DATA", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val exportStr = buildString {
                    appendLine("--- V4 LENSOMETER EXPORT ---")
                    appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                    appendLine("Device: ${android.os.Build.MODEL}")
                    appendLine("Status: ${if (result.success) "SUCCESS" else "FAILED"}")
                    appendLine("Quality: ${if (result.measurementQualityPass) "PASS" else "FAIL"} (${result.qualityMessage})")
                    
                    val p = result.experimentalPower
                    appendLine("")
                    appendLine("--- EXPERIMENTAL POWER ---")
                    appendLine("SPH EXP: ${p?.sphere ?: "N/A"}")
                    appendLine("CYL EXP: ${p?.cylinder ?: "N/A"}")
                    appendLine("AXIS EXP: ${p?.axis ?: "N/A"}")
                    appendLine("Confidence: ${p?.confidence ?: "N/A"}")
                    
                    appendLine("")
                    appendLine("--- RAW OPTICAL FIELD (AGGREGATE) ---")
                    appendLine("Lambda1: ${result.lambda1} ± ${result.lambda1Std}")
                    appendLine("Lambda2: ${result.lambda2} ± ${result.lambda2Std}")
                    appendLine("Isotropic: ${result.isotropic} ± ${result.isotropicStd}")
                    appendLine("Anisotropic: ${result.anisotropic} ± ${result.anisotropicStd}")
                    appendLine("Tracked Dots: ${result.trackedDots}")
                    appendLine("Registration RMS: ${result.registrationRms}")
                    appendLine("Field Fit RMS: ${result.fieldFitRms}")
                    
                    result.allRuns.forEachIndexed { index, run ->
                        appendLine("")
                        appendLine("--- RUN ${index + 1} ---")
                        appendLine("Success: ${run.success}")
                        appendLine("Error: ${run.errorMessage}")
                        if (run.success) {
                            appendLine("Lambda1: ${run.lambda1}")
                            appendLine("Lambda2: ${run.lambda2}")
                            appendLine("Isotropic: ${run.isotropic}")
                            appendLine("Anisotropic: ${run.anisotropic}")
                            val rp = run.experimentalPower
                            appendLine("SPH EXP: ${rp?.sphere ?: "N/A"}")
                            appendLine("CYL EXP: ${rp?.cylinder ?: "N/A"}")
                            appendLine("AXIS EXP: ${rp?.axis ?: "N/A"}")
                        }

                        appendLine("")
                        appendLine("--- DETECTION ---")
                        appendLine("Total Reference Dots: ${run.totalReferenceDots}")
                        appendLine("Total Lens Dots: ${run.totalLensDots}")
                        appendLine("Ref Candidate Blobs: ${run.refCandidateBlobs}")
                        appendLine("Ref Accepted Blobs: ${run.refAcceptedBlobs}")
                        appendLine("Ref Rejected By Area: ${run.refRejectedByArea}")
                        appendLine("Ref Rejected By Circularity: ${run.refRejectedByCircularity}")
                        appendLine("Lens Candidate Blobs: ${run.lensCandidateBlobs}")
                        appendLine("Lens Accepted Blobs: ${run.lensAcceptedBlobs}")
                        appendLine("Lens Rejected By Area: ${run.lensRejectedByArea}")
                        appendLine("Lens Rejected By Circularity: ${run.lensRejectedByCircularity}")

                        appendLine("")
                        appendLine("--- TOPOLOGY ---")
                        appendLine("Topology Input Reference: ${run.topologyInputReferenceDots}")
                        appendLine("Topology Input Lens: ${run.topologyInputLensDots}")
                        appendLine("Topology Assigned Reference: ${run.topologyAssignedReferenceDots}")
                        appendLine("Topology Assigned Lens: ${run.topologyAssignedLensDots}")
                        appendLine("Topology Unassigned Reference: ${run.topologyUnassignedReferenceDots}")
                        appendLine("Topology Unassigned Lens: ${run.topologyUnassignedLensDots}")
                        appendLine("Topology Collisions Reference: ${run.topologyCollisionsReference}")
                        appendLine("Topology Collisions Lens: ${run.topologyCollisionsLens}")
                        appendLine("Topology Consistency Errors Reference: ${run.topologyConsistencyErrorsReference}")
                        appendLine("Topology Consistency Errors Lens: ${run.topologyConsistencyErrorsLens}")
                        appendLine("Estimated Spacing: ${run.estimatedSpacing}")
                        appendLine("Estimated Grid Angle (deg): ${run.estimatedGridAngleDeg}")
                        appendLine("Origin X: ${run.originX} | Origin Y: ${run.originY}")
                        appendLine("Connected Components: ${run.connectedComponentsCount} | Largest Component Size: ${run.largestComponentSize}")
                        appendLine("Spacing Min: ${run.spacingMin} | Median: ${run.spacingMedian} | Mean: ${run.spacingMean}")
                        appendLine("Spacing P25: ${run.spacingP25} | P75: ${run.spacingP75} | Max: ${run.spacingMax}")
                        appendLine("Reference Topology Assignment %: ${run.referenceTopologyAssignmentPct}%")

                        appendLine("")
                        appendLine("--- EXPLICIT UNASSIGNED REASONS ---")
                        appendLine("topologyRejectedNoNeighbor: ${run.topologyRejectedNoNeighbor}")
                        appendLine("topologyRejectedSpacing: ${run.topologyRejectedSpacing}")
                        appendLine("topologyRejectedAngle: ${run.topologyRejectedAngle}")
                        appendLine("topologyRejectedResidual: ${run.topologyRejectedResidual}")
                        appendLine("topologyRejectedDisconnected: ${run.topologyRejectedDisconnected}")
                        appendLine("topologyRejectedCollision: ${run.topologyRejectedCollision}")
                        appendLine("topologyRejectedOutsideGrid: ${run.topologyRejectedOutsideGrid}")
                        appendLine("topologyRejectedOther: ${run.topologyRejectedOther}")
                        appendLine("Fallback Feasibility Note: ${run.fallbackFeasibilityNote}")

                        appendLine("")
                        appendLine("--- MATCHING FUNNEL ---")
                        appendLine("Seed Reference Candidates: ${run.seedReferenceCandidates}")
                        appendLine("Seed Lens Candidates: ${run.seedLensCandidates}")
                        appendLine("Mutual NN Matches: ${run.mutualNearestNeighborMatches}")
                        appendLine("Rejected Distance: ${run.rejectedByDistance}")
                        appendLine("Rejected Non-Mutual: ${run.rejectedByNonMutual}")
                        appendLine("Rejected Topology: ${run.rejectedByTopology}")
                        appendLine("Rejected Duplicate: ${run.rejectedByDuplicateAssignment}")
                        appendLine("Rejected Geometric: ${run.rejectedByGeometricConsistency}")
                        appendLine("Neighbor Expanded: ${run.neighborExpandedMatches}")
                        appendLine("Affine Expanded: ${run.affineExpandedMatches}")
                        appendLine("FINAL ONE-TO-ONE MATCHES: ${run.finalOneToOneMatches}")

                        appendLine("")
                        appendLine("--- SPATIAL DISTRIBUTION ---")
                        appendLine("Q1: ${run.q1Matches}")
                        appendLine("Q2: ${run.q2Matches}")
                        appendLine("Q3: ${run.q3Matches}")
                        appendLine("Q4: ${run.q4Matches}")
                        appendLine("Quadrants Covered: ${run.quadrantCoverage}")
                        appendLine("Spatial Coverage %: ${run.spatialCoveragePct}")

                        appendLine("")
                        appendLine("--- REGISTRATION ---")
                        appendLine("Registration Features: ${run.registrationFeatureCount}")
                        appendLine("Registration Inliers: ${run.registrationInliers}")
                        appendLine("Registration Model: ${run.registrationModel}")
                        appendLine("Registration RMS: ${run.registrationRms}")
                        appendLine("Registration Tx: ${run.registrationTx}")
                        appendLine("Registration Ty: ${run.registrationTy}")
                        appendLine("Registration Rotation: ${run.registrationRotationDeg}")
                        appendLine("Registration Scale: ${run.registrationScale}")

                        appendLine("")
                        appendLine("--- EXISTING REJECTION MAP ---")
                        run.matchRejections.forEach { (key, value) ->
                            appendLine("$key: $value")
                        }
                    }
                }
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportStr))
                android.widget.Toast.makeText(context, "Data copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)) {
                Text("EXPORT TEST DATA")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(16.dp))
            
            
            if (result.success) {
                val overallSuccess = result.allRuns.any { it.success }
                Text("ANALYSIS STATUS: ${if (overallSuccess) "SUCCESS/PARTIAL" else "FAILED"}", color = Color.White)
                if (result.measurementQualityPass) {
                    Text("MEASUREMENT QUALITY: PASS", color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("MEASUREMENT QUALITY: FAIL", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Reason: ${result.qualityMessage}", color = Color.Yellow)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("--------------------------------", color = Color.Gray)
                Text("EXPERIMENTAL OPTICAL ESTIMATE", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("NOT CALIBRATED", color = Color.Red, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (result.experimentalPower != null) {
                    val p = result.experimentalPower
                    Text("SPH EXP: ${p.sphere?.let { String.format("%.2f", it) } ?: "N/A"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("CYL EXP: ${p.cylinder?.let { String.format("%.2f", it) } ?: "N/A"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("AXIS EXP: ${p.axis?.let { String.format("%.0f°", it) } ?: "N/A"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("CONFIDENCE: ${if (p.confidence > 0.5) "HIGH" else "LOW"}", color = Color.LightGray)
                } else {
                    Text("NO EXPERIMENTAL ESTIMATE", color = Color.LightGray)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("--------------------------------", color = Color.Gray)
                Text("RAW OPTICAL FIELD", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Lambda 1: ${String.format("%.6f", result.lambda1)}", color = Color.LightGray)
                Text("Lambda 2: ${String.format("%.6f", result.lambda2)}", color = Color.LightGray)
                Text("Isotropic: ${String.format("%.6f", result.isotropic)}", color = Color.LightGray)
                Text("Anisotropic: ${String.format("%.6f", result.anisotropic)}", color = Color.LightGray)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("--------------------------------", color = Color.Gray)
                result.allRuns.forEachIndexed { index, run ->
                    Text("RUN ${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (run.success) {
                        Text("Analysis: SUCCESS", color = Color.Green)
                        if (run.experimentalPower != null) {
                            val p = run.experimentalPower
                            Text("SPH EXP: ${p.sphere?.let { String.format("%.2f", it) } ?: "N/A"}", color = Color.Cyan)
                            Text("CYL EXP: ${p.cylinder?.let { String.format("%.2f", it) } ?: "N/A"}", color = Color.Cyan)
                            Text("AXIS EXP: ${p.axis?.let { String.format("%.0f°", it) } ?: "N/A"}", color = Color.Cyan)
                        }
                        Text("L1: ${String.format("%.6f", run.lambda1)}", color = Color.LightGray)
                        Text("L2: ${String.format("%.6f", run.lambda2)}", color = Color.LightGray)
                        Text("ISO: ${String.format("%.6f", run.isotropic)}", color = Color.LightGray)
                        Text("ANISO: ${String.format("%.6f", run.anisotropic)}", color = Color.LightGray)
                        Text("Matches: ${run.trackedDots}", color = Color.LightGray)
                        Text("Coverage: ${String.format("%.1f", run.spatialCoveragePct * 100)}%", color = Color.LightGray)
                        Text("Reg RMS: ${String.format("%.2f", run.registrationRms)}", color = Color.LightGray)
                        Text("Fit RMS: ${String.format("%.2f", run.fieldFitRms)}", color = Color.LightGray)
                    } else {
                        Text("Analysis: FAILED", color = Color.Red)
                        Text("Reason: ${run.errorMessage}", color = Color.Yellow)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                    Text("RUN ${index + 1}: (Success: ${run.success})", color = if (run.success) Color.Green else Color.Red, fontWeight = FontWeight.Bold)
                    if (!run.success) {
                        Text("Error: ${run.errorMessage}", color = Color.Red, fontSize = 12.sp)
                    } else {
                        Text("L1: ${String.format("%.4f", run.lambda1)} L2: ${String.format("%.4f", run.lambda2)} Iso: ${String.format("%.4f", run.isotropic)}", color = Color.LightGray)
                    }
                    
                    Text("--- V4 CORRESPONDENCE TELEMETRY ---", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Reference dots: ${run.totalReferenceDots}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Lens dots: ${run.totalLensDots}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Topology ref assigned: ${run.topologyAssignedReferenceDots}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Topology lens assigned: ${run.topologyAssignedLensDots}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Mutual NN: ${run.mutualNearestNeighborMatches}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rejected distance: ${run.rejectedByDistance}", color = Color.Red, fontSize = 12.sp)
                    Text("Rejected non-mutual: ${run.rejectedByNonMutual}", color = Color.Red, fontSize = 12.sp)
                    Text("Rejected topology: ${run.rejectedByTopology}", color = Color.Red, fontSize = 12.sp)
                    Text("Rejected duplicate: ${run.rejectedByDuplicateAssignment}", color = Color.Red, fontSize = 12.sp)
                    Text("Rejected geometric: ${run.rejectedByGeometricConsistency}", color = Color.Red, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Neighbor expanded: ${run.neighborExpandedMatches}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Affine expanded: ${run.affineExpandedMatches}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Final matches: ${run.finalOneToOneMatches}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Q1: ${run.q1Matches} | Q2: ${run.q2Matches} | Q3: ${run.q3Matches} | Q4: ${run.q4Matches}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Coverage: ${String.format("%.1f", run.spatialCoveragePct)}% | Quadrants: ${run.quadrantCoverage}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Registration features: ${run.registrationFeatureCount}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Registration inliers: ${run.registrationInliers}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Registration RMS: ${String.format("%.3f", run.registrationRms)}", color = Color.LightGray, fontSize = 12.sp)
                    
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
