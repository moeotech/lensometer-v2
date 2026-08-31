import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

target = """    }
        
        val allKeypoints = frames.map { detectDots(it) }"""

replacement = """    }

    private fun aggregateFrames(frames: List<Bitmap>): AggResult {
        if (frames.isEmpty()) return AggResult(false, "No frames", emptyList(), 0, 0, 0)
        
        val allKeypoints = frames.map { detectDots(it) }"""

text = text.replace(target, replacement)

with open(path, "w") as f:
    f.write(text)
