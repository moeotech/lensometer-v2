package com.example.analysis.v5

import org.opencv.core.*
import org.opencv.calib3d.Calib3d
import kotlin.math.*

data class MatcherOutput(
    val correspondences: List<V5Correspondence>,
    val telemetry: V5Telemetry,
    val seedIndices: Set<Int>,
    val seedInlierIndices: Set<Int> = emptySet(),
    val seedRejectedIndices: Set<Int> = emptySet(),
    val rawSeedCorrespondences: List<V5Correspondence> = emptyList()
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

    data class AffineResult(
        val a: Double,
        val b: Double,
        val tx: Double,
        val ty: Double,
        val mask: ByteArray
    )

    private fun estimateAffineOrFallback(srcPts: List<Point>, dstPts: List<Point>, threshold: Double): AffineResult {
        val n = srcPts.size
        val defaultMask = ByteArray(n) { 1.toByte() }
        if (n < 2) {
            return AffineResult(1.0, 0.0, 0.0, 0.0, defaultMask)
        }

        try {
            val srcMat = MatOfPoint2f().apply { fromList(srcPts) }
            val dstMat = MatOfPoint2f().apply { fromList(dstPts) }
            val maskMat = Mat()
            val affineMat = Calib3d.estimateAffinePartial2D(srcMat, dstMat, maskMat, Calib3d.RANSAC, threshold)
            srcMat.release()
            dstMat.release()

            if (!affineMat.empty() && affineMat.rows() >= 2) {
                val maskBytes = ByteArray(n)
                maskMat.get(0, 0, maskBytes)
                maskMat.release()
                val affineData = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                affineMat.get(0, 0, affineData)
                affineMat.release()
                val a = affineData[0]
                val b = affineData[1]
                val tx = affineData[2]
                val ty = affineData[5]
                return AffineResult(a, b, tx, ty, maskBytes)
            }
            maskMat.release()
            if (!affineMat.empty()) affineMat.release()
        } catch (e: Throwable) {
            // Fallback for JVM unit tests where OpenCV native libraries are not loaded
        }

        val fallback = estimateSimilarityTransformFallback(srcPts, dstPts)
        val a = fallback[0]
        val b = fallback[1]
        val tx = fallback[2]
        val ty = fallback[5]
        val maskBytes = ByteArray(n) { 1.toByte() }
        for (i in 0 until n) {
            val p = srcPts[i]
            val expX = a * p.x - b * p.y + tx
            val expY = b * p.x + a * p.y + ty
            val act = dstPts[i]
            if (hypot(act.x - expX, act.y - expY) > threshold * 2.5) {
                maskBytes[i] = 0
            }
        }
        return AffineResult(a, b, tx, ty, maskBytes)
    }

    fun match(referencePoints: List<Point>, lensPoints: List<Point>): MatcherOutput {
        if (referencePoints.size < 5 || lensPoints.size < 5) {
            val tel = V5Telemetry(
                referencePointCount = referencePoints.size,
                lensPointCount = lensPoints.size,
                success = false,
                failureReason = "INSUFFICIENT_POINTS"
            )
            return MatcherOutput(emptyList(), tel, emptySet(), emptySet(), emptySet(), emptyList())
        }

        val refStats = calculateSpacingStats(referencePoints)
        val lensStats = calculateSpacingStats(lensPoints)
        val refMedianSpacing = refStats.median

        val refDescriptors = referencePoints.mapIndexed { idx, pt -> V5PointDescriptor.compute(idx, pt, referencePoints, refMedianSpacing) }
        val lensDescriptors = lensPoints.mapIndexed { idx, pt -> V5PointDescriptor.compute(idx, pt, lensPoints, lensStats.median) }

        var rejectedDescriptor = 0
        var rejectedNoCandidate = 0
        val bootstrapRadius = refMedianSpacing * 2.5

        val initialMatches = mutableListOf<Triple<Int, Int, Double>>()
        for ((rIdx, rDesc) in refDescriptors.withIndex()) {
            val rPt = referencePoints[rIdx]
            for ((lIdx, lDesc) in lensDescriptors.withIndex()) {
                val lPt = lensPoints[lIdx]
                val spatialDist = hypot(lPt.x - rPt.x, lPt.y - rPt.y)
                if (spatialDist > bootstrapRadius) {
                    rejectedNoCandidate++
                    continue
                }
                val score = V5PointDescriptor.compare(rDesc, lDesc)
                if (score < 2.5) {
                    initialMatches.add(Triple(rIdx, lIdx, score))
                } else {
                    rejectedDescriptor++
                }
            }
        }
        val initialCandidateCount = initialMatches.size

        // Find raw seeds (mutual nearest neighbors with spatial bootstrap)
        val rawSeedPairs = mutableListOf<Pair<Int, Int>>()
        for ((rIdx, rDesc) in refDescriptors.withIndex()) {
            val rPt = referencePoints[rIdx]
            var bestLensIdx = -1
            var bestScore = Double.MAX_VALUE
            for ((lIdx, lDesc) in lensDescriptors.withIndex()) {
                val lPt = lensPoints[lIdx]
                if (hypot(lPt.x - rPt.x, lPt.y - rPt.y) > bootstrapRadius) continue
                val score = V5PointDescriptor.compare(rDesc, lDesc)
                if (score < bestScore) {
                    bestScore = score
                    bestLensIdx = lIdx
                }
            }
            if (bestLensIdx != -1 && bestScore < 2.5) {
                val lDesc = lensDescriptors[bestLensIdx]
                val lPt = lensPoints[bestLensIdx]
                var bestBackRefIdx = -1
                var bestBackScore = Double.MAX_VALUE
                for ((rI, rD) in refDescriptors.withIndex()) {
                    val rp = referencePoints[rI]
                    if (hypot(lPt.x - rp.x, lPt.y - rp.y) > bootstrapRadius) continue
                    val s = V5PointDescriptor.compare(rD, lDesc)
                    if (s < bestBackScore) {
                        bestBackScore = s
                        bestBackRefIdx = rI
                    }
                }
                if (bestBackRefIdx == rIdx) {
                    rawSeedPairs.add(Pair(rIdx, bestLensIdx))
                }
            }
        }

        val seedMatchesRaw = rawSeedPairs.size

        val seedInliersMap = mutableMapOf<Int, Int>()
        val seedInlierIndices = mutableSetOf<Int>()
        val seedRejectedIndices = mutableSetOf<Int>()
        val rawSeedCorrespondences = mutableListOf<V5Correspondence>()

        var seedRansacInliers = 0
        var seedRansacRejected = 0
        var seedRansacRms = 0.0
        var rejectedSeedRansac = 0

        if (rawSeedPairs.size < 4) {
            seedRansacRejected = rawSeedPairs.size
            rejectedSeedRansac = rawSeedPairs.size
            for ((rI, lI) in rawSeedPairs) {
                seedRejectedIndices.add(rI)
                rawSeedCorrespondences.add(
                    V5Correspondence(referencePoints[rI], lensPoints[lI], referencePoints[rI], rI, lI, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false)
                )
            }
            val tel = V5Telemetry(
                referencePointCount = referencePoints.size,
                lensPointCount = lensPoints.size,
                referenceMedianSpacing = refMedianSpacing,
                lensMedianSpacing = lensStats.median,
                referenceSpacingStats = refStats,
                lensSpacingStats = lensStats,
                initialCandidateMatches = initialCandidateCount,
                seedMatchCount = 0,
                seedMatchesRaw = seedMatchesRaw,
                seedRansacInliers = 0,
                seedRansacRejected = seedRansacRejected,
                seedRansacRms = Double.MAX_VALUE,
                rejectedSeedRansac = rejectedSeedRansac,
                rejectedDescriptor = rejectedDescriptor,
                rejectedNoCandidate = rejectedNoCandidate,
                success = false,
                failureReason = "SEED_GEOMETRY_UNRELIABLE"
            )
            return MatcherOutput(emptyList(), tel, emptySet(), seedInlierIndices, seedRejectedIndices, rawSeedCorrespondences)
        }

        val srcPts = rawSeedPairs.map { referencePoints[it.first] }
        val dstPts = rawSeedPairs.map { lensPoints[it.second] }
        val ransacThresh = max(3.0, refMedianSpacing * 0.35)
        val affResult = estimateAffineOrFallback(srcPts, dstPts, ransacThresh)

        val aInit = affResult.a
        val bInit = affResult.b
        val txInit = affResult.tx
        val tyInit = affResult.ty
        val maskBytes = affResult.mask

        var sumSeedSq = 0.0
        for (i in rawSeedPairs.indices) {
            val (rI, lI) = rawSeedPairs[i]
            val rPt = referencePoints[rI]
            val lPt = lensPoints[lI]
            val isRansacInlier = (maskBytes[i].toInt() != 0)

            if (isRansacInlier) {
                seedInliersMap[rI] = lI
                seedInlierIndices.add(rI)
                seedRansacInliers++
                val expX = aInit * rPt.x - bInit * rPt.y + txInit
                val expY = bInit * rPt.x + aInit * rPt.y + tyInit
                sumSeedSq += hypot(lPt.x - expX, lPt.y - expY).pow(2)
                rawSeedCorrespondences.add(
                    V5Correspondence(rPt, lPt, Point(expX, expY), rI, lI, lPt.x - rPt.x, lPt.y - rPt.y, hypot(lPt.x - expX, lPt.y - expY), 0.95, 0.9, 0.9, true)
                )
            } else {
                seedRejectedIndices.add(rI)
                seedRansacRejected++
                rejectedSeedRansac++
                rawSeedCorrespondences.add(
                    V5Correspondence(rPt, lPt, rPt, rI, lI, lPt.x - rPt.x, lPt.y - rPt.y, 0.0, 0.0, 0.0, 0.0, false)
                )
            }
        }

        seedRansacRms = if (seedRansacInliers > 0) sqrt(sumSeedSq / seedRansacInliers) else Double.MAX_VALUE

        if (seedRansacInliers < 4 || seedRansacRms > refMedianSpacing * 0.75) {
            val tel = V5Telemetry(
                referencePointCount = referencePoints.size,
                lensPointCount = lensPoints.size,
                referenceMedianSpacing = refMedianSpacing,
                lensMedianSpacing = lensStats.median,
                referenceSpacingStats = refStats,
                lensSpacingStats = lensStats,
                initialCandidateMatches = initialCandidateCount,
                seedMatchCount = seedRansacInliers,
                seedMatchesRaw = seedMatchesRaw,
                seedRansacInliers = seedRansacInliers,
                seedRansacRejected = seedRansacRejected,
                seedRansacRms = seedRansacRms,
                rejectedSeedRansac = rejectedSeedRansac,
                rejectedTransform = if (seedRansacRms > refMedianSpacing * 0.75) 1 else 0,
                rejectedDescriptor = rejectedDescriptor,
                rejectedNoCandidate = rejectedNoCandidate,
                success = false,
                failureReason = if (seedRansacInliers < 4) "SEED_GEOMETRY_UNRELIABLE" else "TRANSFORM_RMS_TOO_HIGH"
            )
            return MatcherOutput(emptyList(), tel, seedInlierIndices, seedInlierIndices, seedRejectedIndices, rawSeedCorrespondences)
        }

        var currentInliers = seedInliersMap.toMutableMap()
        var predTx = txInit
        var predTy = tyInit
        var predRotDeg = Math.toDegrees(atan2(bInit, aInit))
        var predScale = sqrt(aInit * aInit + bInit * bInit)
        var predRms = seedRansacRms
        var iterationCount = 0
        val matchesAddedPerIteration = mutableListOf<Int>()

        var a = aInit
        var b = bInit

        var totalRejectedResidual = 0
        var totalRejectedLocal = 0
        var totalRejectedCollision = 0
        var totalRejectedTransform = 0
        var totalCandBeforeRes = 0
        var totalCandAfterRes = 0
        var totalCandAfterLocal = 0
        var totalCandAfterOneToOne = 0

        val maxIterations = 4
        for (iter in 0 until maxIterations) {
            iterationCount++
            if (currentInliers.size < 4) break

            val srcPtsList = mutableListOf<Point>()
            val dstPtsList = mutableListOf<Point>()
            for ((rI, lI) in currentInliers) {
                srcPtsList.add(referencePoints[rI])
                dstPtsList.add(lensPoints[lI])
            }

            val affResult = estimateAffineOrFallback(srcPtsList, dstPtsList, refMedianSpacing * 0.35)
            a = affResult.a
            b = affResult.b
            predTx = affResult.tx
            predTy = affResult.ty
            predScale = sqrt(a * a + b * b)
            predRotDeg = Math.toDegrees(atan2(b, a))

            var sumSq = 0.0
            for (i in srcPtsList.indices) {
                val p = srcPtsList[i]
                val expX = a * p.x - b * p.y + predTx
                val expY = b * p.x + a * p.y + predTy
                val act = dstPtsList[i]
                sumSq += hypot(act.x - expX, act.y - expY).pow(2)
            }
            predRms = sqrt(sumSq / srcPtsList.size)

            if (predRms > refMedianSpacing * 0.75) {
                totalRejectedTransform++
                break
            }

            val allCandidatePairs = mutableListOf<Triple<Int, Int, Double>>()
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

            totalCandBeforeRes += allCandidatePairs.size
            if (allCandidatePairs.isEmpty()) break

            val residuals = allCandidatePairs.map { it.third }
            val (medRes, madRes) = calculateMedianAndMAD(residuals)
            val adaptiveThreshold = max(3.0, min(medRes + 3.0 * 1.4826 * madRes, refMedianSpacing * 0.35))

            val validCandidates = mutableListOf<Triple<Int, Int, Double>>()
            var rejRes = 0
            var rejLocal = 0

            for (cand in allCandidatePairs) {
                val rI = cand.first
                val lI = cand.second
                val res = cand.third

                if (res > adaptiveThreshold) {
                    rejRes++
                    continue
                }

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

                if (!localConsistent) {
                    rejLocal++
                    continue
                }

                validCandidates.add(cand)
            }

            totalRejectedResidual += rejRes
            totalRejectedLocal += rejLocal
            totalCandAfterRes += validCandidates.size
            totalCandAfterLocal += validCandidates.size

            val bestRefToLens = mutableMapOf<Int, Triple<Int, Double, Double>>()
            val bestLensToRef = mutableMapOf<Int, Triple<Int, Double, Double>>()

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
            var rejCollision = 0
            for ((rI, trip) in bestRefToLens) {
                val lI = trip.first
                if (bestLensToRef[lI]?.first == rI) {
                    nextInliers[rI] = lI
                } else {
                    rejCollision++
                }
            }

            totalRejectedCollision += rejCollision
            totalCandAfterOneToOne += nextInliers.size

            val addedCount = nextInliers.size - currentInliers.size
            matchesAddedPerIteration.add(max(0, addedCount))
            currentInliers = nextInliers

            if (abs(addedCount) <= 1 && iter > 0) break
        }

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
            val isSeed = seedInlierIndices.contains(rI)

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
            seedMatchCount = seedRansacInliers,
            seedMatchesRaw = seedMatchesRaw,
            seedRansacInliers = seedRansacInliers,
            seedRansacRejected = seedRansacRejected,
            seedRansacRms = seedRansacRms,
            preValidationMatches = initialCandidateCount,
            acceptedInlierMatches = finalCorrespondences.size,
            rejectedSeedRansac = rejectedSeedRansac,
            rejectedResidual = totalRejectedResidual,
            rejectedLocalConsistency = totalRejectedLocal,
            rejectedCollision = totalRejectedCollision,
            rejectedTransform = totalRejectedTransform,
            rejectedNoCandidate = rejectedNoCandidate,
            rejectedDescriptor = rejectedDescriptor,
            candidatesBeforeResidualGate = totalCandBeforeRes,
            candidatesAfterResidualGate = totalCandAfterRes,
            candidatesAfterLocalGate = totalCandAfterLocal,
            candidatesAfterOneToOne = totalCandAfterOneToOne,
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

        return MatcherOutput(finalCorrespondences, telemetry, seedInlierIndices, seedInlierIndices, seedRejectedIndices, rawSeedCorrespondences)
    }
}
