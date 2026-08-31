import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
            val unmatchedLens = baseLensPoints.filter { !acceptedMatches.containsValue(it) }
            rejections["unmatched_lens_dots"] = unmatchedLens.size
            
            val cx = w / 2.0
            val cy = h / 2.0
            var q1 = 0; var q2 = 0; var q3 = 0; var q4 = 0
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
            
            val quads = listOf(q1, q2, q3, q4).count { it > 0 }
            val coverage = if (baseRefPoints.isNotEmpty()) candidateRef.size.toDouble() / baseRefPoints.size else 0.0
            
            if (candidateRef.size < 20 || quads < 3 || coverage < 0.4) {
                return@withContext V4RunResult(
                    success = false, 
                    errorMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE (Matches: ${candidateRef.size}, Quads: $quads, Coverage: ${String.format("%.1f", coverage*100)}%)",
                    measurementQualityPass = false,
                    qualityMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE"
                )
            }
            
            // --- STABILITY GATE ---
"""

start_str = """
            val unmatchedLens = baseLensPoints.filter { !acceptedMatches.containsValue(it) }
            rejections["unmatched_lens_dots"] = unmatchedLens.size
"""

end_str = """            // --- STABILITY GATE ---"""

start_idx = text.find(start_str)
end_idx = text.find(end_str, start_idx) + len(end_str)

target = text[start_idx:end_idx]
text = text.replace(target, replacement)

with open(path, "w") as f:
    f.write(text)
