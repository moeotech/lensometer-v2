import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

target = """            // --- END STABILITY GATE ---
            for (pt in candidateRef) {
                if (pt.x < cx && pt.y < cy) q1++
                if (pt.x >= cx && pt.y < cy) q2++
                if (pt.x < cx && pt.y >= cy) q3++
                if (pt.x >= cx && pt.y >= cy) q4++
            }
            rejections["Quad1_Matches"] = q1
            rejections["Quad2_Matches"] = q2
            rejections["Quad3_Matches"] = q3
            rejections["Quad4_Matches"] = q4
            
            val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections)"""

replacement = """            // --- END STABILITY GATE ---
            val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections)"""

text = text.replace(target, replacement)
with open(path, "w") as f:
    f.write(text)
