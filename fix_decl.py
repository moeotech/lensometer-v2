import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

target = """            }

            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE"""

replacement = """            }

            var opticalFieldRetainedCount = 0
            val finalPtsToMeasureRef = mutableListOf<Point>()
            var q1 = false; var q2 = false; var q3 = false; var q4 = false
            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE"""

text = text.replace(target, replacement)
with open(path, "w") as f:
    f.write(text)
