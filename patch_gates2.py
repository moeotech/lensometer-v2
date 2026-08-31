import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

target = """            if (opticalFieldRetainedCount < 6) {
                 return V4RunResult(success = false, errorMessage = "Insufficient retained measurement points (<6)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections)
            }
            
            if (quadCount < 2 || spreadX < w * 0.1 || spreadY < h * 0.1) {
                rejections["roi_rejection"] = rejections.getOrDefault("roi_rejection", 0) + opticalFieldRetainedCount
                return V4RunResult(success = false, errorMessage = "Poor spatial coverage (quads: $quadCount, spread: ${spreadX.toInt()}x${spreadY.toInt()})", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }"""

replacement = """            if (opticalFieldRetainedCount < 20) {
                 return V4RunResult(success = false, errorMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE (Points < 20)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections)
            }
            
            if (quadCount < 3 || spatialCoveragePct < 40.0) {
                rejections["roi_rejection"] = rejections.getOrDefault("roi_rejection", 0) + opticalFieldRetainedCount
                return V4RunResult(success = false, errorMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE (Quads: $quadCount, Cov: ${String.format(\"%.1f\", spatialCoveragePct)}%)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }
            
            if (!opticalCenterValid) {
                return V4RunResult(success = false, errorMessage = "INVALID CENTER (ill-conditioned fit)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }"""

text = text.replace(target, replacement)
with open(path, "w") as f:
    f.write(text)
