import os

path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

# 1. Add retryCountForCurrentRun
if "var retryCountForCurrentRun" not in text:
    text = text.replace("var currentRunIndex by remember { mutableStateOf(0) }", "var currentRunIndex by remember { mutableStateOf(0) }\n    var retryCountForCurrentRun by remember { mutableStateOf(0) }")

# 2. Modify captureFrames
old_capture = """        while (capturedCount < 30) {
            delay(50)
        }
        frameCaptureCallback = null
        isProcessing = false"""

new_capture = """        while (capturedCount < 30 && isProcessing) {
            delay(50)
        }
        frameCaptureCallback = null
        if (!isProcessing) {
            throw kotlinx.coroutines.CancellationException("Capture cancelled")
        }
        isProcessing = false"""
text = text.replace(old_capture, new_capture)

# 3. Add UI text and Stop button
old_ui_top = """            Text("V4 DIRECT LENS", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Camera ID: $cameraIdStr", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isProcessing) {"""

new_ui_top = """            Text("V4 DIRECT LENS", color = Color.White, fontWeight = FontWeight.Bold)
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
                        runResults.fill(null)
                        overallResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("STOP TEST", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (isProcessing) {"""
text = text.replace(old_ui_top, new_ui_top)

# 4. Modify ANALYZING block
import re

analyzing_block_old = r"""                                if \(!result\.success\) \{
                                    analysisErrorMessage = "INSUFFICIENT OPTICAL FEATURES - HOLD STILL\\n\$\{result\.errorMessage\}"
                                    // Retry same run
                                    currentStep = V4Step\.STEP_2_WITH_LENS
                                    withLensFrames\.clear\(\)
                                    camera2ControlRef\?\.let \{ c2c ->
                                        val builder = CaptureRequestOptions\.Builder\(\)
                                        builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AE_LOCK, false\)
                                        builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AWB_LOCK, false\)
                                        builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AF_MODE, CaptureRequest\.CONTROL_AF_MODE_CONTINUOUS_PICTURE\)
                                        c2c\.captureRequestOptions = builder\.build\(\)
                                    \}
                                \} else \{
                                    analysisErrorMessage = ""
                                    runResults\[currentRunIndex\] = result
                                    if \(currentRunIndex < 2\) \{
                                        currentRunIndex\+\+
                                        currentStep = V4Step\.STEP_2_WITH_LENS
                                        withLensFrames\.clear\(\)
                                        // Unlock AE for next run
                                        camera2ControlRef\?\.let \{ c2c ->
                                            val builder = CaptureRequestOptions\.Builder\(\)
                                            builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AE_LOCK, false\)
                                            builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AWB_LOCK, false\)
                                            builder\.setCaptureRequestOption\(CaptureRequest\.CONTROL_AF_MODE, CaptureRequest\.CONTROL_AF_MODE_CONTINUOUS_PICTURE\)
                                            c2c\.captureRequestOptions = builder\.build\(\)
                                        \}
                                    \} else \{
                                        val validRuns = runResults\.filterNotNull\(\)\.filter \{ it\.success \}
                                        overallResult = V4OpticalAnalyzer\.calculateRepeatability\(validRuns\)
                                        currentStep = V4Step\.COMPLETE
                                    \}
                                \}"""

analyzing_block_new = """                                if (!result.success && retryCountForCurrentRun < 2) {
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
                                                success = false,
                                                errorMessage = "insufficient valid runs",
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
                                                lastRunResult = runResults.lastOrNull()
                                            )
                                        } else {
                                            overallResult = V4OpticalAnalyzer.calculateRepeatability(validRuns)
                                        }
                                        currentStep = V4Step.COMPLETE
                                    }
                                }"""

text = re.sub(analyzing_block_old, analyzing_block_new, text, flags=re.DOTALL)


# 5. Modify V4ResultDialog !success state
old_dialog_fail = """            } else {
                Text("REPEATABILITY FAILED or QUALITY GATE FAILED", color = Color.Red, fontSize = 20.sp)
                Text("Reason: ${result.errorMessage}", color = Color.White)
            }"""

new_dialog_fail = """            } else {
                Text("MEASUREMENT QUALITY: FAIL", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Reason: ${result.errorMessage}", color = Color.Yellow)
            }"""

text = text.replace(old_dialog_fail, new_dialog_fail)

# 6. Add Error message if any
old_analysis_err = """                    LaunchedEffect(Unit) {"""
new_analysis_err = """                    if (analysisErrorMessage.isNotEmpty()) {
                        Text(analysisErrorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    LaunchedEffect(Unit) {"""
text = text.replace(old_analysis_err, new_analysis_err)

with open(path, "w") as f:
    f.write(text)

print("Patched.")
