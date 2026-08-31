import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """data class V4Result(
    val success: Boolean,
    val errorMessage: String = "",
    val measurementQualityPass: Boolean = false,
    val qualityMessage: String = "","""

text = text.replace("data class V4Result(\n    val success: Boolean,\n    val errorMessage: String = \"\",", replacement, 1)

with open(path, "w") as f:
    f.write(text)
