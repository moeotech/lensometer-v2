
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
