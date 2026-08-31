import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

text = text.replace("    }\n\n        \n        val allKeypoints = frames.map { detectDots(it) }", "    }\n\n    private fun aggregateFrames(frames: List<Bitmap>): AggResult {\n        if (frames.isEmpty()) return AggResult(false, \"No frames\", emptyList(), 0, 0, 0)\n        \n        val allKeypoints = frames.map { detectDots(it) }")

with open(path, "w") as f:
    f.write(text)
