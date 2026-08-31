import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """data class V4Result(
    val success: Boolean,
    val errorMessage: String = "",
    val measurementQualityPass: Boolean = false,
    val qualityMessage: String = "",
    val sphDisplay: String = "",
    val cylDisplay: String = "",
    val axisDisplay: String = "",
    val lambda1: Double = 0.0,
    val lambda2: Double = 0.0,
    val isotropic: Double = 0.0,
    val anisotropic: Double = 0.0,
    val lambda1Std: Double = 0.0,
    val lambda2Std: Double = 0.0,
    val isotropicStd: Double = 0.0,
    val anisotropicStd: Double = 0.0,
    val commonGridPointsAcrossRuns: Int = 0,
    val correspondenceConsistency: Double = 0.0,
    val centerStdPx: Double = 0.0,
    val tensorStd: Double = 0.0,
"""

text = text.replace("data class V4Result(\n    val success: Boolean,\n    val errorMessage: String = \"\",\n    val measurementQualityPass: Boolean = false,\n    val qualityMessage: String = \"\",\n    val sphDisplay: String = \"\",\n    val cylDisplay: String = \"\",\n    val axisDisplay: String = \"\",\n    val lambda1: Double = 0.0,\n    val lambda2: Double = 0.0,\n    val isotropic: Double = 0.0,\n    val anisotropic: Double = 0.0,\n    val lambda1Std: Double = 0.0,\n    val lambda2Std: Double = 0.0,\n    val isotropicStd: Double = 0.0,\n    val anisotropicStd: Double = 0.0,\n", replacement)

with open(path, "w") as f:
    f.write(text)
