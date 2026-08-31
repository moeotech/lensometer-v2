sed -i '/DisposableEffect(lifecycleOwner) {/i\
    LaunchedEffect(isStable, phase) {\
        if (isStable && phase == LensExperimentPhase.ALIGN_LENS) {\
            autoCaptureTriggered = false\
            progress = 0f\
            val steps = 20\
            val stepTime = 1000L / steps\
            for (i in 1..steps) {\
                delay(stepTime)\
                progress = i.toFloat() / steps\
            }\
            autoCaptureTriggered = true\
        } else {\
            progress = 0f\
            autoCaptureTriggered = false\
        }\
    }\
\
    LaunchedEffect(autoCaptureTriggered) {\
        if (autoCaptureTriggered && phase == LensExperimentPhase.ALIGN_LENS) {\
            phase = LensExperimentPhase.CAPTURE_LENS\
            alignMessage = "MEASURING..."\
            runCaptureSequence(withLensFrames, lockAE = false)\
            restoreAuto()\
            phase = LensExperimentPhase.PROCESSING\
            val res = withContext(Dispatchers.Default) {\
                LensAnalyzer.analyze(noLensFrames, withLensFrames, stableLensGeom)\
            }\
            runResults.add(res)\
            phase = LensExperimentPhase.RESULTS\
        }\
    }\
' app/src/main/java/com/example/ui/LensExperimentScreen.kt
