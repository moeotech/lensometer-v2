import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

text = text.replace("if (bestLens != null && bestDist < spacing * 0.4) {", "if (bestLens != null && bestDist < spacing * 0.8) {")

with open(path, "w") as f:
    f.write(text)
