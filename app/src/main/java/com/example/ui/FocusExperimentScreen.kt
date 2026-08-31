package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.analysis.FocusAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.sqrt

data class FocusPoint(
    val focusDistance: Float, // Diopters
    val sharpness: Float,
    val iso: Int,
    val exposureTimeNs: Long,
    val timestamp: Long
)

data class FocusRunResult(
    val noLensPoints: List<FocusPoint>,
    val lensPoints: List<FocusPoint>
) {
    val f0: Float? get() = noLensPoints.maxByOrNull { it.sharpness }?.focusDistance
    val f1: Float? get() = lensPoints.maxByOrNull { it.sharpness }?.focusDistance
    val deltaF: Float? get() = if (f0 != null && f1 != null) f1!! - f0!! else null
    val peakNoLens: Float? get() = noLensPoints.maxByOrNull { it.sharpness }?.sharpness
    val peakLens: Float? get() = lensPoints.maxByOrNull { it.sharpness }?.sharpness
}

enum class FocusV2Step {
    CAPABILITIES,
    SWEEP_NO_LENS,
    SWEEP_WITH_LENS,
    RESULT
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun FocusExperimentScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

        LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var currentStep by remember { mutableStateOf(FocusV2Step.CAPABILITIES) }
    var currentRunIndex by remember { mutableStateOf(0) }
    
    val runResults = remember { mutableStateListOf<FocusRunResult?>(null, null, null) }
    var currentNoLensPoints = remember { mutableStateListOf<FocusPoint>() }
    var currentLensPoints = remember { mutableStateListOf<FocusPoint>() }

    var manualFocusSupported by remember { mutableStateOf(false) }
    var minFocusDistance by remember { mutableStateOf(0f) }
    var afModes by remember { mutableStateOf("") }
    var aeLockSupported by remember { mutableStateOf(false) }
    var awbLockSupported by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }

    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var camera2ControlRef by remember { mutableStateOf<Camera2CameraControl?>(null) }

    var flashMode by remember { mutableStateOf("AUTO") }
    LaunchedEffect(flashMode, cameraControlRef) {
        try {
            when (flashMode) {
                "ON" -> cameraControlRef?.enableTorch(true)
                "OFF" -> cameraControlRef?.enableTorch(false)
                "AUTO" -> {
                    cameraControlRef?.enableTorch(false)
                    camera2ControlRef?.let { c2c ->
                        val builder = CaptureRequestOptions.Builder()
                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        c2c.captureRequestOptions = builder.build()
                    }
                }
            }
        } catch (e: Exception) {}
    }

    var previewRef by remember { mutableStateOf<Preview?>(null) }

    var frameCaptureCallback by remember { mutableStateOf<((ImageProxy) -> Unit)?>(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    
    var isProcessing by remember { mutableStateOf(false) }
    var sweepProgress by remember { mutableStateOf(0f) }

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
                                        val sharpness = com.example.analysis.FocusAnalyzer.measureCenterSharpness(imageProxy)
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }
                        imageAnalysisRef = imageAnalysis
                    
                        try {
                                                        val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControlRef = camera.cameraControl
                            camera2ControlRef = Camera2CameraControl.from(camera.cameraControl)
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

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun calculateFocusScore(bitmap: android.graphics.Bitmap): Double {
    val mat = org.opencv.core.Mat()
    org.opencv.android.Utils.bitmapToMat(bitmap, mat)
    val gray = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)
    val laplacian = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.Laplacian(gray, laplacian, org.opencv.core.CvType.CV_64F)
    val mean = org.opencv.core.MatOfDouble()
    val stddev = org.opencv.core.MatOfDouble()
    org.opencv.core.Core.meanStdDev(laplacian, mean, stddev)
    val stddevVal = stddev.get(0, 0)[0]
    mat.release()
    gray.release()
    laplacian.release()
    mean.release()
    stddev.release()
    return stddevVal * stddevVal
}
