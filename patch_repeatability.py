import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

rep = """
        val coordSets = results.map { it.matchedGridCoords.toSet() }
        val commonCoords = if (coordSets.isNotEmpty()) coordSets.reduce { acc, set -> acc.intersect(set) } else emptySet()
        val commonGridPointsAcrossRuns = commonCoords.size
        
        val unionCoords = if (coordSets.isNotEmpty()) coordSets.reduce { acc, set -> acc.union(set) } else emptySet()
        val correspondenceConsistency = if (unionCoords.isNotEmpty()) commonGridPointsAcrossRuns.toDouble() / unionCoords.size else 0.0
        
        val cxMean = results.map { it.opticalCenterX }.average()
        val cyMean = results.map { it.opticalCenterY }.average()
        val centerStdPx = Math.sqrt(results.map { 
            Math.pow(it.opticalCenterX - cxMean, 2.0) + Math.pow(it.opticalCenterY - cyMean, 2.0) 
        }.average())
        
        val tA11Mean = results.map { it.tensorA11 }.average()
        val tA12Mean = results.map { it.tensorA12 }.average()
        val tA21Mean = results.map { it.tensorA21 }.average()
        val tA22Mean = results.map { it.tensorA22 }.average()
        
        val tensorStd = Math.sqrt(results.map { 
            Math.pow(it.tensorA11 - tA11Mean, 2.0) + Math.pow(it.tensorA12 - tA12Mean, 2.0) +
            Math.pow(it.tensorA21 - tA21Mean, 2.0) + Math.pow(it.tensorA22 - tA22Mean, 2.0)
        }.average())

        val qualityPass = results.all { it.measurementQualityPass }
"""

text = text.replace("        val qualityPass = results.all { it.measurementQualityPass }", rep)

rep2 = """            anisotropicStd = anisotropicStd,
            allRuns = results,
            trackedDots = results.firstOrNull()?.trackedDots ?: 0,
            registrationRms = results.map { it.registrationRms }.average(),
            registrationInliers = results.map { it.registrationInliers }.average().toInt(),
            fieldFitRms = results.map { it.fieldFitRms }.average(),
            commonGridPointsAcrossRuns = commonGridPointsAcrossRuns,
            correspondenceConsistency = correspondenceConsistency,
            centerStdPx = centerStdPx,
            tensorStd = tensorStd
        )"""

text = re.sub(r'            anisotropicStd = anisotropicStd,\n            allRuns = results,\n            trackedDots = results.firstOrNull\(\)\?.trackedDots \?: 0,\n            registrationRms = results.map \{ it.registrationRms \}.average\(\),\n            registrationInliers = results.map \{ it.registrationInliers \}.average\(\).toInt\(\),\n            fieldFitRms = results.map \{ it.fieldFitRms \}.average\(\)\n        \)', rep2, text)

with open(path, "w") as f:
    f.write(text)
