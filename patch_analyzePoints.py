import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

sig_old = "fun analyzePoints(matchedRef: List<Point>, matchedLens: List<Point>, w: Double, h: Double, baseRefDotCount: Int = 0, baseLensDotCount: Int = 0, spacing: Double = 30.0, rejections: MutableMap<String, Int> = mutableMapOf()): V4RunResult {"
sig_new = "fun analyzePoints(matchedRef: List<Point>, matchedLens: List<Point>, w: Double, h: Double, baseRefDotCount: Int = 0, baseLensDotCount: Int = 0, spacing: Double = 30.0, rejections: MutableMap<String, Int> = mutableMapOf(), matchedGridCoords: List<Pair<Int, Int>> = emptyList()): V4RunResult {"
text = text.replace(sig_old, sig_new)

ret_old = "                opticalCenterX = opticalCenterX,"
ret_new = "                matchedGridCoords = matchedGridCoords,\n                opticalCenterX = opticalCenterX,"
text = text.replace(ret_old, ret_new)

with open(path, "w") as f:
    f.write(text)
