cat << 'INNER_EOF' > temp_disp.kt
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
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                try {
                                    val bitmap = proxyToBitmap(imageProxy)
                                    if (bitmap != null) {
                                        val focusScore = calculateFocusScore(bitmap)
                                        currentFocusScore = focusScore
                                        if (focusScore > maxFocusScore) {
                                            maxFocusScore = focusScore
                                        }
                                        
                                        if (phase == FocusPhase.MEASURING) {
                                            frameCaptureCallback?.invoke(bitmap, focusScore)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
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
/DisposableEffect\(lifecycleOwner\) \{/ {
    in_block = 1
    system("cat temp_disp.kt")
    next
}
/suspend fun runCaptureSequence/ {
    if (in_block) {
        in_block = 0
    }
}
{
    if (!in_block) print
}
' app/src/main/java/com/example/ui/FocusExperimentScreen.kt > temp_screen.kt
mv temp_screen.kt app/src/main/java/com/example/ui/FocusExperimentScreen.kt
