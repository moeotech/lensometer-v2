import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

text = text.replace("    val dispMax: Double = 0.0,\n", "    val dispMax: Double = 0.0,\n    val matchedGridCoords: List<Pair<Int, Int>> = emptyList(),\n")

with open(path, "w") as f:
    f.write(text)
