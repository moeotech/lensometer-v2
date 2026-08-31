import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """                opticalCenterX = opticalCenterX,
                opticalCenterY = opticalCenterY,
                opticalCenterConditionNumber = opticalCenterConditionNumber,
                opticalCenterValid = opticalCenterValid,
                opticalCenterConfidence = opticalCenterConfidence,
"""

text = text.replace("                opticalCenterX = opticalCenterX,\n                opticalCenterY = opticalCenterY,\n", replacement)

# We also need to add commonGridPointsAcrossRuns, correspondenceConsistency, centerStdPx, tensorStd to V4Result
with open(path, "w") as f:
    f.write(text)
