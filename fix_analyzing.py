import os
path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

target = """                        coroutineScope.launch {
                            val result = V4OpticalAnalyzer.analyze(noLensFrames, withLensFrames)
                            
                            if (!result.success) {
                                analysisErrorMessage = "INSUFFICIENT OPTICAL FEATURES - HOLD STILL\\n${result.errorMessage}"
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
                                    overallResult = V4OpticalAnalyzer.calculateRepeatability(validRuns)
                                    currentStep = V4Step.COMPLETE
                                }
                            }
                        }"""

replacement = """                        coroutineScope.launch {
                            val result = V4OpticalAnalyzer.analyze(noLensFrames, withLensFrames)
                            
                            if (!result.success && retryCountForCurrentRun < 2) {
                                retryCountForCurrentRun++
                                analysisErrorMessage = "INSUFFICIENT OPTICAL FEATURES - HOLD STILL\\n${result.errorMessage}"
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
                        }"""

if target in text:
    text = text.replace(target, replacement)
    print("Replaced!")
else:
    print("Not found!")
    print(text[text.find("coroutineScope.launch {"):text.find("coroutineScope.launch {")+1000])

with open(path, "w") as f:
    f.write(text)
