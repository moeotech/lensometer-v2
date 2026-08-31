import re

# Patch Analyzer
with open('app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "val registrationTy: Double = 0.0,\n    val registrationScale: Double = 0.0\n)",
    "val registrationTy: Double = 0.0,\n    val registrationScale: Double = 0.0,\n    val axis: Double = 0.0,\n    val experimentalPower: ExperimentalPowerEstimate? = null\n)"
)

content = content.replace(
    "val framesAccepted: Int = 0,\n",
    "val framesAccepted: Int = 0,\n    val experimentalPower: ExperimentalPowerEstimate? = null,\n"
)

content = re.sub(
    r"return@withContext V4RunResult\((.*?framesAccepted = [^\n\)]+)\n\s*\)",
    r"return@withContext V4RunResult(\1,\n        experimentalPower = V4OpticalCalibration.estimatePower(finalLambda1, finalLambda2, finalAxis, true, finalFitRms)\n    )",
    content, flags=re.DOTALL
)

start_idx = content.find("suspend fun calculateRepeatability(results: List<V4RunResult>): V4Result = withContext(Dispatchers.Default) {")
end_idx = content.find("fun drawVectorMap(", start_idx)

new_func = """suspend fun calculateRepeatability(allRuns: List<V4RunResult>): V4Result = withContext(Dispatchers.Default) {
        val validRuns = allRuns.filter { it.success }
        if (validRuns.isEmpty()) {
            val errorMsg = allRuns.mapIndexed { i, r -> "Run ${i+1}: ${r.errorMessage}" }.joinToString("\\n")
            return@withContext V4Result(
                success = true,
                errorMessage = errorMsg,
                measurementQualityPass = false,
                qualityMessage = "insufficient valid runs",
                allRuns = allRuns,
                lastRunResult = allRuns.lastOrNull()
            )
        }
        
        val lambda1Mean = validRuns.map { it.lambda1 }.average()
        val lambda2Mean = validRuns.map { it.lambda2 }.average()
        val isotropicMean = validRuns.map { it.isotropic }.average()
        val anisotropicMean = validRuns.map { it.anisotropic }.average()

        val lambda1Std = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.lambda1 - lambda1Mean) * (it.lambda1 - lambda1Mean) }.average()) else 0.0
        val lambda2Std = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.lambda2 - lambda2Mean) * (it.lambda2 - lambda2Mean) }.average()) else 0.0
        val isotropicStd = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.isotropic - isotropicMean) * (it.isotropic - isotropicMean) }.average()) else 0.0
        val anisotropicStd = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.anisotropic - anisotropicMean) * (it.anisotropic - anisotropicMean) }.average()) else 0.0

        val cvThreshold = 0.30
        val minSignal = 0.05
        
        fun checkCv(mean: Double, std: Double, name: String): String? {
            if (Math.abs(mean) > minSignal && validRuns.size > 1) {
                val cv = std / Math.abs(mean)
                if (cv > cvThreshold) return "$name CV=${String.format(\"%.2f\", cv)}"
            }
            return null
        }
        
        val fails = listOfNotNull(
            checkCv(lambda1Mean, lambda1Std, "L1"),
            checkCv(lambda2Mean, lambda2Std, "L2"),
            checkCv(isotropicMean, isotropicStd, "ISO"),
            checkCv(anisotropicMean, anisotropicStd, "ANISO")
        )
        
        val l1_vals = validRuns.map { it.lambda1 }.sorted()
        val l2_vals = validRuns.map { it.lambda2 }.sorted()
        val iso_vals = validRuns.map { it.isotropic }.sorted()
        val aniso_vals = validRuns.map { it.anisotropic }.sorted()

        val lambda1Med = if (validRuns.size % 2 == 1) l1_vals[validRuns.size / 2] else (l1_vals[validRuns.size / 2 - 1] + l1_vals[validRuns.size / 2]) / 2.0
        val lambda2Med = if (validRuns.size % 2 == 1) l2_vals[validRuns.size / 2] else (l2_vals[validRuns.size / 2 - 1] + l2_vals[validRuns.size / 2]) / 2.0
        val isoMed = if (validRuns.size % 2 == 1) iso_vals[validRuns.size / 2] else (iso_vals[validRuns.size / 2 - 1] + iso_vals[validRuns.size / 2]) / 2.0
        val anisoMed = if (validRuns.size % 2 == 1) aniso_vals[validRuns.size / 2] else (aniso_vals[validRuns.size / 2 - 1] + aniso_vals[validRuns.size / 2]) / 2.0
        
        val qualPass = validRuns.size == 3 && fails.isEmpty()
        val qualMsg = if (validRuns.size < 3) "insufficient valid runs" else if (fails.isNotEmpty()) "Variance too high: ${fails.joinToString(", ")}" else "Pass"
        
        var sumSin = 0.0
        var sumCos = 0.0
        validRuns.forEach {
            val a = it.axis * 2.0
            sumSin += kotlin.math.sin(a)
            sumCos += kotlin.math.cos(a)
        }
        var axisOutput = if (validRuns.isNotEmpty()) kotlin.math.atan2(sumSin, sumCos) / 2.0 else 0.0
        if (axisOutput < 0) axisOutput += kotlin.math.PI
        
        return@withContext V4Result(
            success = true,
            measurementQualityPass = qualPass,
            qualityMessage = qualMsg,
            lambda1 = lambda1Med,
            lambda2 = lambda2Med,
            isotropic = isoMed,
            anisotropic = anisoMed,
            lambda1Std = lambda1Std,
            lambda2Std = lambda2Std,
            isotropicStd = isotropicStd,
            anisotropicStd = anisotropicStd,
            commonGridPointsAcrossRuns = validRuns.minOfOrNull { it.trackedDots } ?: 0,
            correspondenceConsistency = 1.0,
            centerStdPx = 0.0,
            tensorStd = 0.0,
            allRuns = allRuns,
            trackedDots = validRuns.map { it.trackedDots }.average().toInt(),
            registrationRms = validRuns.map { it.registrationRms }.average(),
            registrationInliers = validRuns.map { it.registrationInliers }.average().toInt(),
            fieldFitRms = validRuns.map { it.fieldFitRms }.average(),
            refDotCount = validRuns.first().refDotCount,
            lensDotCount = validRuns.map { it.lensDotCount }.average().toInt(),
            meanDx = validRuns.map { it.meanDx }.average(),
            meanDy = validRuns.map { it.meanDy }.average(),
            visualVectorMap = validRuns.lastOrNull()?.let { null },
            lastRunResult = validRuns.lastOrNull(),
            registrationModel = validRuns.lastOrNull()?.registrationModel ?: "",
            registrationRotationDeg = validRuns.map { it.registrationRotationDeg }.average(),
            registrationTx = validRuns.map { it.registrationTx }.average(),
            registrationTy = validRuns.map { it.registrationTy }.average(),
            registrationScale = validRuns.map { it.registrationScale }.average(),
            axis = axisOutput,
            experimentalPower = V4OpticalCalibration.estimatePower(lambda1Med, lambda2Med, axisOutput, qualPass, validRuns.map { it.fieldFitRms }.average())
        )
    }
    
    """

content = content[:start_idx] + new_func + content[end_idx:]

with open('app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt', 'w') as f:
    f.write(content)

# Patch UI
with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    uicontent = f.read()

pattern_analyzing = r"if \(!result\.success && retryCountForCurrentRun < 2\) \{.*?else \{"
replacement_analyzing = """
                            analysisErrorMessage = if (!result.success) "Run failed: ${result.errorMessage}" else ""
                            runResults[currentRunIndex] = result
                            retryCountForCurrentRun = 0
                            
                            if (false) {
                            } else {"""
uicontent = re.sub(pattern_analyzing, replacement_analyzing, uicontent, flags=re.DOTALL)

uicontent = re.sub(
    r"val validRuns = runResults\.filterNotNull\(\)\.filter \{ it\.success \}.*?overallResult = V4OpticalAnalyzer\.calculateRepeatability\(validRuns\)\n\s*\}",
    r"overallResult = V4OpticalAnalyzer.calculateRepeatability(runResults.filterNotNull())",
    uicontent, flags=re.DOTALL
)

pattern_dialog = r"if \(result\.success\) \{.*?Text\(\"Anisotropic: \$\{String\.format\(\"%\.6f\", result\.anisotropic\)\}\", color = Color\.LightGray\)"
replacement_dialog = """
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
                    Text("SPH EXP: ${p.sphere?.let { String.format(\"%.2f\", it) } ?: \"N/A\"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("CYL EXP: ${p.cylinder?.let { String.format(\"%.2f\", it) } ?: \"N/A\"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("AXIS EXP: ${p.axis?.let { String.format(\"%.0f°\", it) } ?: \"N/A\"}", color = Color.Cyan, fontSize = 20.sp)
                    Text("CONFIDENCE: ${if (p.confidence > 0.5) "HIGH" else "LOW"}", color = Color.LightGray)
                } else {
                    Text("NO EXPERIMENTAL ESTIMATE", color = Color.LightGray)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("--------------------------------", color = Color.Gray)
                Text("RAW OPTICAL FIELD", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Lambda 1: ${String.format(\"%.6f\", result.lambda1)}", color = Color.LightGray)
                Text("Lambda 2: ${String.format(\"%.6f\", result.lambda2)}", color = Color.LightGray)
                Text("Isotropic: ${String.format(\"%.6f\", result.isotropic)}", color = Color.LightGray)
                Text("Anisotropic: ${String.format(\"%.6f\", result.anisotropic)}", color = Color.LightGray)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("--------------------------------", color = Color.Gray)
                result.allRuns.forEachIndexed { index, run ->
                    Text("RUN ${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (run.success) {
                        Text("Analysis: SUCCESS", color = Color.Green)
                        if (run.experimentalPower != null) {
                            val p = run.experimentalPower
                            Text("SPH EXP: ${p.sphere?.let { String.format(\"%.2f\", it) } ?: \"N/A\"}", color = Color.Cyan)
                            Text("CYL EXP: ${p.cylinder?.let { String.format(\"%.2f\", it) } ?: \"N/A\"}", color = Color.Cyan)
                            Text("AXIS EXP: ${p.axis?.let { String.format(\"%.0f°\", it) } ?: \"N/A\"}", color = Color.Cyan)
                        }
                        Text("L1: ${String.format(\"%.6f\", run.lambda1)}", color = Color.LightGray)
                        Text("L2: ${String.format(\"%.6f\", run.lambda2)}", color = Color.LightGray)
                        Text("ISO: ${String.format(\"%.6f\", run.isotropic)}", color = Color.LightGray)
                        Text("ANISO: ${String.format(\"%.6f\", run.anisotropic)}", color = Color.LightGray)
                        Text("Matches: ${run.trackedDots}", color = Color.LightGray)
                        Text("Coverage: ${String.format(\"%.1f\", run.spatialCoveragePct * 100)}%", color = Color.LightGray)
                        Text("Reg RMS: ${String.format(\"%.2f\", run.registrationRms)}", color = Color.LightGray)
                        Text("Fit RMS: ${String.format(\"%.2f\", run.fieldFitRms)}", color = Color.LightGray)
                    } else {
                        Text("Analysis: FAILED", color = Color.Red)
                        Text("Reason: ${run.errorMessage}", color = Color.Yellow)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
"""
uicontent = re.sub(pattern_dialog, replacement_dialog, uicontent, flags=re.DOTALL)

export_code = """
            Button(onClick = { onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("CLOSE")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)) {
                Text("EXPORT TEST DATA")
            }
            Spacer(modifier = Modifier.height(16.dp))
"""
uicontent = uicontent.replace("Text(\"V4 DIRECT LENS RESULT\", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)", 
                          "Text(\"V4 DIRECT LENS RESULT\", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)\n" + export_code)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(uicontent)

