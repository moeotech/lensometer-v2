import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
        val qualityPass = results.all { it.measurementQualityPass }
        val qualityMsgs = results.mapIndexed { idx, it -> "R${idx+1}: ${it.qualityMessage}" }.filter { !it.endsWith(": ") }.joinToString(" | ")
        
        var axisDisplay = "UNRELIABLE"

        var axisMean = 0.0
        if (anisotropicMean > 0.02) {
            var sinSum = 0.0
            var cosSum = 0.0
            for (r in results) {
                val rad = r.axis * 2.0 * Math.PI / 180.0
                sinSum += Math.sin(rad)
                cosSum += Math.cos(rad)
            }
            axisMean = Math.atan2(sinSum / results.size, cosSum / results.size) * 180.0 / (2.0 * Math.PI)
            if (axisMean < 0) axisMean += 180.0
            if (axisMean >= 180.0) axisMean -= 180.0
            axisDisplay = String.format("%.0f° SIGNAL", axisMean)
        }
        
        val lastRun = results.last()
        val visualVectorMap = if (lastRun.referencePoints.isNotEmpty()) { drawVectorMapInternal(lastRun, 1f, true) } else null
        
        return@withContext V4Result(
            success = true,
            measurementQualityPass = qualityPass && fails.isEmpty() && spreadFails.isEmpty(),
            qualityMessage = if (!qualityPass) qualityMsgs else if (fails.isNotEmpty() || spreadFails.isNotEmpty()) (fails + spreadFails).joinToString(", ") else "Stable",
            sphDisplay = "NOT CALIBRATED",
            cylDisplay = "NOT CALIBRATED",
            axisDisplay = axisDisplay,"""

# We need to replace the error returning block for cvThreshold and spread
target_start = """        if (fails.isNotEmpty() || spreadFails.isNotEmpty()) {
            val reason = (fails + spreadFails).joinToString(", ")
            return@withContext V4Result(success = false, errorMessage = "MEASUREMENT UNSTABLE ($reason)", allRuns = results, lastRunResult = results.last())
        }


        var axisDisplay = "UNRELIABLE""""

# Find the end of the return statement
target_full = text[text.find(target_start):text.find("axisDisplay = axisDisplay,") + len("axisDisplay = axisDisplay,")]

text = text.replace(target_full, replacement)

with open(path, "w") as f:
    f.write(text)
