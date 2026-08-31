import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """            return@withContext res.copy(
                stabilityL1Std = l1Std,
                stabilityL2Std = l2Std,
                stabilityIsoStd = isoStd,
                stabilityAnisoStd = anisoStd,
                measurementQualityPass = stabilityPass && res.success,
                qualityMessage = stabilityMsg,
                framesCaptured = withLensFrames.size,"""

text = text.replace("            return@withContext res.copy(\n                framesCaptured = withLensFrames.size,", replacement)

with open(path, "w") as f:
    f.write(text)
