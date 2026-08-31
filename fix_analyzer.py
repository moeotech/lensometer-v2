import re

with open('app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt', 'r') as f:
    content = f.read()

content = content.replace("success = true,\n                errorMessage = errorMsg,\n                measurementQualityPass = false,", "success = false,\n                errorMessage = errorMsg,\n                measurementQualityPass = false,")

with open('app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt', 'w') as f:
    f.write(content)
