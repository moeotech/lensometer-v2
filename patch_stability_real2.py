import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
            // --- STABILITY GATE ---
            val stabilityResults = mutableListOf<V4RunResult>()
            val checkCount = kotlin.math.min(15, withLensFrames.size)
            val checkFrames = withLensFrames.takeLast(checkCount)
            for (frame in checkFrames) {
                val fPts = detectDots(frame)
                val fRef = mutableListOf<Point>()
                val fLens = mutableListOf<Point>()
                
                for (i in candidateLens.indices) {
                    val exp = candidateLens[i]
                    var bestDist = Double.MAX_VALUE
                    var bestPt: org.opencv.core.Point? = null
                    for (p in fPts) {
                        val d = Math.hypot(p.x - exp.x, p.y - exp.y)
                        if (d < bestDist && d < spacing * 0.4) {
                            bestDist = d
                            bestPt = p
                        }
                    }
                    if (bestPt != null) {
                        fRef.add(candidateRef[i])
                        fLens.add(bestPt)
                    }
                }
                
                if (fRef.size >= 10) {
                    val fRes = analyzePoints(fRef, fLens, w, h, baseRefPoints.size, fPts.size, spacing, mutableMapOf())
                    if (fRes.success) {
                        stabilityResults.add(fRes)
                    }
                }
            }
            
            var stabilityPass = false
            var stabilityMsg = ""
            var l1Std = 0.0
            var l2Std = 0.0
            var isoStd = 0.0
            var anisoStd = 0.0
            var inliersStd = 0.0
            var cxStd = 0.0
            
            if (stabilityResults.size >= 5) {
                val l1s = stabilityResults.map { it.lambda1 }
                val l2s = stabilityResults.map { it.lambda2 }
                val isos = stabilityResults.map { it.isotropic }
                val anisos = stabilityResults.map { it.anisotropic }
                val inliers = stabilityResults.map { it.opticalFieldRetainedCount.toDouble() }
                val cxs = stabilityResults.map { it.opticalCenterX }
                
                fun std(list: List<Double>): Double {
                    val mean = list.average()
                    return Math.sqrt(list.map { (it - mean) * (it - mean) }.average())
                }
                l1Std = std(l1s)
                l2Std = std(l2s)
                isoStd = std(isos)
                anisoStd = std(anisos)
                inliersStd = std(inliers)
                cxStd = std(cxs)
                
                val fails = mutableListOf<String>()
                if (l1Std > 0.005) fails.add("L1 noise (std=${String.format("%.4f", l1Std)})")
                if (l2Std > 0.005) fails.add("L2 noise (std=${String.format("%.4f", l2Std)})")
                if (isoStd > 0.005) fails.add("ISO noise (std=${String.format("%.4f", isoStd)})")
                if (anisoStd > 0.005) fails.add("ANISO noise (std=${String.format("%.4f", anisoStd)})")
                if (inliersStd > 3.0) fails.add("Unstable inliers (std=${String.format("%.1f", inliersStd)})")
                if (cxStd > 20.0) fails.add("Wandering center (std=${String.format("%.1f", cxStd)})")
                
                if (fails.isEmpty()) {
                    stabilityPass = true
                } else {
                    stabilityMsg = fails.joinToString(", ")
                }
            } else {
                stabilityMsg = "Not enough valid frames for stability check"
            }
            // --- END STABILITY GATE ---
"""

# find "var q1 = 0;" and insert right before it
idx = text.find("var q1 = 0; var q2 = 0; var q3 = 0; var q4 = 0")
text = text[:idx] + replacement + text[idx:]

with open(path, "w") as f:
    f.write(text)
