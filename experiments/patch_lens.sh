cat << 'INNER_EOF' > temp_disp_start.kt
    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var imageAnalysisRef: ImageAnalysis? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    previewRef = preview
                    
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
INNER_EOF

cat << 'INNER_EOF' > temp_disp_end.kt
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
                    } catch (exc: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                if (cameraProviderFuture.isDone) {
                    val provider = cameraProviderFuture.get()
                    imageAnalysisRef?.clearAnalyzer()
                    provider.unbindAll()
                }
            }
        }
        
        lifecycle.addObserver(observer)
        
        onDispose {
            lifecycle.removeObserver(observer)
            if (cameraProviderFuture.isDone) {
                val provider = cameraProviderFuture.get()
                imageAnalysisRef?.clearAnalyzer()
                provider.unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }
INNER_EOF

awk '
BEGIN { block_start = 0; block_end = 0; printed = 0 }
/DisposableEffect\(lifecycleOwner\) \{/ {
    if (!printed) {
        block_start = 1
        system("cat temp_disp_start.kt")
        next
    }
}
/analysis.setAnalyzer\(analysisExecutor\)/ {
    if (block_start && !block_end) {
        in_analyzer = 1
        print
        next
    }
}
/imageAnalysisRef = imageAnalysis/ {
    if (in_analyzer) {
        in_analyzer = 0
        block_end = 1
        system("cat temp_disp_end.kt")
        next
    }
}
/suspend fun runCaptureSequence/ {
    if (block_start) {
        block_start = 0
        block_end = 0
        printed = 1
    }
}
{
    if (in_analyzer) {
        print
    } else if (!block_start) {
        print
    }
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp_screen.kt
mv temp_screen.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
