import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """    val opticalCenterX: Double = 0.0,
    val opticalCenterY: Double = 0.0,
    
    val stabilityL1Std: Double = 0.0,
    val stabilityL2Std: Double = 0.0,
    val stabilityIsoStd: Double = 0.0,
    val stabilityAnisoStd: Double = 0.0,
    val measurementQualityPass: Boolean = false,
    val qualityMessage: String = "","""

text = text.replace("    val opticalCenterX: Double = 0.0,\n    val opticalCenterY: Double = 0.0,", replacement, 1)

with open(path, "w") as f:
    f.write(text)
