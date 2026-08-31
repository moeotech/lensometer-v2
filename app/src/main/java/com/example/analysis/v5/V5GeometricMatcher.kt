package com.example.analysis.v5

import org.opencv.core.*
import kotlin.math.*

data class MatcherOutput(
    val correspondences: List<V5Correspondence>,
    val telemetry: V5Telemetry,
    val seedIndices: Set<Int>
)

object V5GeometricMatcher {

    private fun calculateSpacingStats(points: List<Point>): SpacingStats {
        if (points.size < 2) return SpacingStats(0.0, 0.0, 50.0, 0.0, 0.0, 50.0)
        val dists = mutableListOf<Double>()
        for (p1 in points) {
            var minDist = Double.MAX_VALUE
            for (p2 in points) {
                if (p1 === p2) continue
                val d = hypot(p1.x - p2.x, p1.y - p2.y)
                if (d < minDist) minDist = d
            }
            if (minDist != Double.MAX_VALUE) dists.add(minDist)
        }
        if (dists.isEmpty()) return SpacingStats(0.0, 0.0, 50.0, 0.0, 0.0, 50.0)
        dists.sort()
        val min = dists.first()
        val max = dists.last()
        val median = dists[dists.size / 2]
        val p25 = dists[dists.size / 4]
        val p75 = dists[(dists.size * 3) / 4]
        val mean = dists.average()
        return SpacingStats(min, p25, median, p75, max, mean)
    }

    private fun calculateMedianAndMAD(values: List<Double>): Pair<Double, Double> {
        if (values.isEmpty()) return Pair(0.0, 0.0)
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        val absDevs = sorted.map { abs(it - median) }.sorted()
        val mad = absDevs[absDevs.size / 2]
        return Pair(median, mad)
    }

    private fun estimateSimilarityTransformFallback(src: List<Point>, dst: List<Point>): DoubleArray {
        val n = src.size
        if (n < 2) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        var cxSrc = 0.0; var cySrc = 0.0
        var cxDst = 0.0; var cyDst = 0.0
        for (i in 0 until n) {
            cxSrc += src[i].x; cySrc += src[i].y
            cxDst += dst[i].x; cyDst += dst[i].y
        }
        cxSrc /= n; cySrc /= n
        cxDst /= n; cyDst /= n

        var num = 0.0
        var den = 0.0
        var varSrc = 0.0

        for (i in 0 until n) {
            val x1 = src[i].x - cxSrc
            val y1 = src[i].y - cySrc
            val x2 = dst[i].x - cxDst
            val y2 = dst[i].y - cyDst

            num += (x1 * y2 - y1 * x2)
            den += (x1 * x2 + y1 * y2)
            varSrc += (x1 * x1 + y1 * y1)
        }

        val theta = atan2(num, den)
        val scale = if (varSrc > 1e-5) den / varSrc else 1.0
        val cosT = cos(theta) * scale
        val sinT = sin(theta) * scale

        val tx = cxDst - (cosT * cxSrc - sinT * cySrc)
        val ty = cyDst - (sinT * cxSrc + cosT * cySrc)

        return doubleArrayOf(cosT, sinT, tx, -sinT, cosT, ty)
    }

    fun match(referencePoints: List<Point>, lensPoints: List<Point>): MatcherOutput {
        if (referencePoints.size < 5 || lensPoints.size < 5) {
            val tel = V5Telemetry(
                referencePointCount = referencePoints.size,
                lensPointCount = lensPoints.size,
                success = false,
                failureReason = "INSUFFICIENT_POINTS"
            )
            return MatcherOutput(emptyList(), tel, emptySet())
        }

        // Stage 3: Estimate target spacing
        val refStats = calculateSpacingStats(referencePoints)
        val lensStats = calculateSpacingStats(lensPoints)
        val refMedianSpacing = refStats.median

        // Stage 2: Compute descriptors
        val refDescriptors = referencePoints.mapIndexed { idx, pt -> V5PointDescriptor.compute(idx, pt, referencePoints, refMedianSpacing) }
        val lensDescriptors = lensPoints.mapIndexed { idx, pt -> V5PointDescriptor.compute(idx, pt, lensPoints, lensStats.median) }

        // Initial candidate pairs & Seeds
        val initialMatches = mutableListOf<Triple<Int, Int, Double>>() // refIndex, lensIndex, descriptorScore
        for ((rIdx, rDesc) in refDescriptors.withIndex()) {
            for ((lIdx, lDesc) in lensDescriptors.withIndex()) {
                val score = V5PointDescriptor.compare(rDesc, lDesc)
                if (score < 2.5) {
                    initialMatches.add(Triple(rIdx, lIdx, score))
                }
            }
        }
        val initialCandidateCount = initialMatches.size

        // Find mutual nearest neighbor seeds in descriptor space
        val seedMatchesMap = mutableMapOf<Int, Int>()
        val seedIndices = mutableSetOf<Int>()

        for ((rIdx, rDesc) in refDescriptors.withIndex()) {
            var bestLensIdx = -1
            var bestScore = Double.MAX_VALUE
            for ((lIdx, lDesc) in lensDescriptors.withIndex()) {
                val score = V5PointDescriptor.compare(rDesc, lDesc)
                if (score < bestScore) {
                    bestScore = score
                    bestLensIdx = lIdx
                }
            }
            if (bestLensIdx != -1 && bestScore < 2.5) {
                val lDesc = lensDescriptors[bestLensIdx]
                var bestBackRefIdx = -1
                var bestBackScore = Double.MAX_VALUE
                for ((rI, rD) in refDescriptors.withIndex()) {
                    val s = V5PointDescriptor.compare(rD, lDesc)
                    if (s < bestBackScore) {
                        bestBackScore = s
                        bestBackRefIdx = rI
                    }
                }
                if (bestBackRefIdx == rIdx) {
                    seedMatchesMap[rIdx] = bestLensIdx
                    seedIndices.add(rIdx)
                }
            }
        }

        // Iterative Transform Refinement & Expansion (Tasks 1, 2, 3, 4, 5)
        var currentInliers = seedMatchesMap.toMutableMap()
        var predTx = 0.0
        var predTy = 0.0
        var predRotDeg = 0.0
        var predScale = 1.0
        var predRms = 0.0
        var iterationCount = 0
        val matchesAddedPerIteration = mutableListOf<Int>()

        var a = 1.0
        var b = 0.0

        val maxIterations = 4
        for (iter in 0 until maxIterations) {
            iterationCount++
            if (currentInliers.size < 4) break

            val srcPts = mutableListOf<Point>()
            val dstPts = mutableListOf<Point>()
            for ((rI, lI) in currentInliers) {
                srcPts.add(referencePoints[rI])
                dstPts.add(lensPoints[lI])
            }

            val affineData = estimateSimilarityTransformFallback(srcPts, dstPts)
            a = affineData[0]
            b = affineData[1]
            predTx = affineData[2]
            predTy = affineData[5]
            predScale = sqrt(a * a + b * b)
            predRotDeg = Math.toDegrees(atan2(b, a))

            // Calculate RMS on inliers
            var sumSq = 0.0
            for (i in srcPts.indices) {
                val p = srcPts[i]
                val expX = a * p.x - b * p.y + predTx
                val expY = b * p.x + a * p.y + predTy
                val act = dstPts[i]
                sumSq += hypot(act.x - expX, act.y - expY).pow(2)
            }
            predRms = sqrt(sumSq / srcPts.size)

            // Expand or re-evaluate using adaptive residual threshold (Task 1 & 2)
            val allCandidatePairs = mutableListOf<Triple<Int, Int, Double>>() // refIndex, lensIndex, residual

            val searchRadius = refMedianSpacing * 1.2
            for ((rI, rPt) in referencePoints.withIndex()) {
                val expX = a * rPt.x - b * rPt.y + predTx
                val expY = b * rPt.x + a * rPt.y + predTy

                for ((lI, lPt) in lensPoints.withIndex()) {
                    val res = hypot(lPt.x - expX, lPt.y - expY)
                    if (res <= searchRadius) {
                        allCandidatePairs.add(Triple(rI, lI, res))
                    }
                }
            }

            if (allCandidatePairs.isEmpty()) break

            val residuals = allCandidatePairs.map { it.third }
            val (medRes, madRes) = calculateMedianAndMAD(residuals)
            // Task 2: Adaptive residual threshold
            val adaptiveThreshold = max(3.0, min(medRes + 3.0 * 1.4826 * madRes, refMedianSpacing * 0.35))

            val validCandidates = mutableListOf<Triple<Int, Int, Double>>() // refIndex, lensIndex, residual
            for (cand in allCandidatePairs) {
                val rI = cand.first
                val lI = cand.second
                val res = cand.third

                if (res > adaptiveThreshold) continue

                // Task 3: Displacement & local consistency check
                val rPt = referencePoints[rI]
                val lPt = lensPoints[lI]
                val dx = lPt.x - rPt.x
                val dy = lPt.y - rPt.y

                var localConsistent = true
                if (currentInliers.isNotEmpty()) {
                    val nearbyInliers = currentInliers.entries.filter { (inR, _) ->
                        hypot(referencePoints[inR].x - rPt.x, referencePoints[inR].y - rPt.y) <= refMedianSpacing * 2.0
                    }
                    if (nearbyInliers.isNotEmpty()) {
                        val meanNeighborDx = nearbyInliers.map { (inR, inL) -> lensPoints[inL].x - referencePoints[inR].x }.average()
                        val meanNeighborDy = nearbyInliers.map { (inR, inL) -> lensPoints[inL].y - referencePoints[inR].y }.average()
                        val motionDiff = hypot(dx - meanNeighborDx, dy - meanNeighborDy)
                        if (motionDiff > refMedianSpacing * 0.3) {
                            localConsistent = false
                        }
                    }
                }

                if (!localConsistent) continue

                validCandidates.add(cand)
            }

            // Task 4: One-to-one collision enforcement (keep best combined score)
            val bestRefToLens = mutableMapOf<Int, Triple<Int, Double, Double>>() // refIndex -> (lensIndex, residual, score)
            val bestLensToRef = mutableMapOf<Int, Triple<Int, Double, Double>>() // lensIndex -> (refIndex, residual, score)

            for (cand in validCandidates) {
                val rI = cand.first
                val lI = cand.second
                val res = cand.third
                val descScore = initialMatches.find { it.first == rI && it.second == lI }?.third ?: 1.0
                val combinedScore = res + descScore * 10.0

                if (!bestRefToLens.containsKey(rI) || combinedScore < bestRefToLens[rI]!!.third) {
                    bestRefToLens[rI] = Triple(lI, res, combinedScore)
                }
                if (!bestLensToRef.containsKey(lI) || combinedScore < bestLensToRef[lI]!!.third) {
                    bestLensToRef[lI] = Triple(rI, res, combinedScore)
                }
            }

            val nextInliers = mutableMapOf<Int, Int>()
            for ((rI, trip) in bestRefToLens) {
                val lI = trip.first
                if (bestLensToRef[lI]?.first == rI) {
                    nextInliers[rI] = lI
                }
            }

            val addedCount = nextInliers.size - currentInliers.size
            matchesAddedPerIteration.add(max(0, addedCount))
            currentInliers = nextInliers

            if (abs(addedCount) <= 1 && iter > 0) break
        }

        // Build final correspondences and evaluate quality gates (Task 6)
        val finalCorrespondences = mutableListOf<V5Correspondence>()
        val acceptedMap = currentInliers

        val cx = referencePoints.map { it.x }.average()
        val cy = referencePoints.map { it.y }.average()

        var q1 = 0; var q2 = 0; var q3 = 0; var q4 = 0
        val acceptedResiduals = mutableListOf<Double>()

        for ((rI, lI) in acceptedMap) {
            val rPt = referencePoints[rI]
            val lPt = lensPoints[lI]
            val expX = a * rPt.x - b * rPt.y + predTx
            val expY = b * rPt.x + a * rPt.y + predTy
            val resPx = hypot(lPt.x - expX, lPt.y - expY)
            acceptedResiduals.add(resPx)

            val rawDx = lPt.x - rPt.x
            val rawDy = lPt.y - rPt.y
            val isSeed = seedIndices.contains(rI)

            if (rPt.x < cx && rPt.y < cy) q1++
            else if (rPt.x >= cx && rPt.y < cy) q2++
            else if (rPt.x < cx && rPt.y >= cy) q3++
            else q4++

            finalCorrespondences.add(
                V5Correspondence(
                    referencePoint = rPt,
                    observedPoint = lPt,
                    predictedPoint = Point(expX, expY),
                    referenceIndex = rI,
                    lensIndex = lI,
                    rawDx = rawDx,
                    rawDy = rawDy,
                    residualPx = resPx,
                    matchConfidence = if (isSeed) 0.95 else 0.85,
                    descriptorScore = 0.9,
                    localConsistencyScore = 0.9,
                    isInlier = true
                )
            )
        }

        val quadrantsCovered = listOf(q1, q2, q3, q4).count { it > 0 }
        val coveragePct = (finalCorrespondences.size.toDouble() / referencePoints.size) * 100.0

        val (medRes, madRes) = calculateMedianAndMAD(acceptedResiduals)
        val maxRes = acceptedResiduals.maxOrNull() ?: 0.0

        // Task 6: Quality gates
        val correspondenceSuccess = finalCorrespondences.size >= 10 && quadrantsCovered >= 3 && coveragePct >= 35.0
        val measurementQualityValid = predRms <= refMedianSpacing * 0.25 && medRes <= refMedianSpacing * 0.15 && maxRes <= refMedianSpacing * 0.35
        val success = correspondenceSuccess && measurementQualityValid

        val telemetry = V5Telemetry(
            referencePointCount = referencePoints.size,
            lensPointCount = lensPoints.size,
            referenceMedianSpacing = refMedianSpacing,
            lensMedianSpacing = lensStats.median,
            referenceSpacingStats = refStats,
            lensSpacingStats = lensStats,
            initialCandidateMatches = initialCandidateCount,
            seedMatchCount = seedMatchesMap.size,
            preValidationMatches = initialCandidateCount,
            acceptedInlierMatches = finalCorrespondences.size,
            rejectedResidual = 0,
            rejectedLocalConsistency = 0,
            rejectedCollision = 0,
            rejectedTransform = 0,
            medianResidualPx = medRes,
            residualMadPx = madRes,
            maxAcceptedResidualPx = maxRes,
            transformInliers = acceptedMap.size,
            transformRms = predRms,
            predictionTx = predTx,
            predictionTy = predTy,
            predictionRotationDeg = predRotDeg,
            predictionScale = predScale,
            iterationCount = iterationCount,
            matchesAddedPerIteration = matchesAddedPerIteration,
            q1Matches = q1,
            q2Matches = q2,
            q3Matches = q3,
            q4Matches = q4,
            quadrantsCovered = quadrantsCovered,
            coveragePct = coveragePct,
            correspondenceSuccess = correspondenceSuccess,
            measurementQualityValid = measurementQualityValid,
            success = success,
            failureReason = if (success) "" else if (!correspondenceSuccess) "INSUFFICIENT_CORRESPONDENCES" else "POOR_MEASUREMENT_QUALITY"
        )

        return MatcherOutput(finalCorrespondences, telemetry, seedIndices)
    }
}
