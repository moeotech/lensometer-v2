with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "V4Step.ANALYZING -> {" in line:
        skip = True
        new_lines.append("""                V4Step.ANALYZING -> {
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
""")
    if skip:
        if "if (currentStep == V4Step.COMPLETE && overallResult != null) {" in line:
            skip = False
            new_lines.append(line)
    else:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.writelines(new_lines)
