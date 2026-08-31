package com.example.analysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class OpticalPair(
    val reference: Point,
    val observed: Point,
    val displacement: Point,
    val originalIndex: Int,
    var status: String = "RETAINED",
    var correctedDisplacement: Point = Point(0.0, 0.0),
    var robustWeight: Double = 1.0
)

data class V4RunResult(

    val success: Boolean,
    val errorMessage: String = "",
    val axis: Double = 0.0,
    val lambda1: Double = 0.0,
    val lambda2: Double = 0.0,
    val isotropic: Double = 0.0,
    val anisotropic: Double = 0.0,
    val trackedDots: Int = 0,
    
    val topologyMatchCount: Int = 0,
    val registrationFeatureCount: Int = 0,
    val registrationInliers: Int = 0,
    val registrationRms: Double = 0.0,
    
    val opticalFieldInputCount: Int = 0,
    val opticalFieldRetainedCount: Int = 0,
    val fieldFitRms: Double = 0.0,
    
    val refDotCount: Int = 0,
    val lensDotCount: Int = 0,
    val meanDx: Double = 0.0,
    val meanDy: Double = 0.0,
    val referencePoints: List<Point> = emptyList(),
    val observedPoints: List<Point> = emptyList(),
    val refWidth: Int = 0,
    val refHeight: Int = 0,
    val registrationModel: String = "",
    val registrationRotationDeg: Double = 0.0,
    val registrationTx: Double = 0.0,
    val registrationTy: Double = 0.0,
    val registrationScale: Double = 0.0,
    val candidateMatches: Int = 0,
    val acceptedMatches: Int = 0,
    val matchRejections: Map<String, Int> = emptyMap(),
    val spatialCoveragePct: Double = 0.0,
    val quadrantCoverage: Int = 0,
    val matrixRank: Int = 0,
    val conditionNumber: Double = 0.0,
    val degeneracyStatus: String = "",
    val framesCaptured: Int = 0,
    val framesAccepted: Int = 0,
    val experimentalPower: ExperimentalPowerEstimate? = null,
    val framesRejected: Int = 0,
    val temporalTrackCount: Int = 0,
    val stableTrackCount: Int = 0,
    val medianTrackLifetime: Double = 0.0,
    val rejectedReferencePoints: List<Point> = emptyList(),
    val unmatchedLensPoints: List<Point> = emptyList(),
    val localOutlierRejections: Int = 0,
    val crossingVectorRejections: Int = 0,
    val medianLocalResidual: Double = 0.0,
    val madLocalResidual: Double = 0.0,
    val opticalRejectedReferencePoints: List<Point> = emptyList(),
    val opticalRejectedObservedPoints: List<Point> = emptyList(),
    val dispMedian: Double = 0.0,
    val dispMAD: Double = 0.0,
    val dispP90: Double = 0.0,
    val dispMax: Double = 0.0,
    val matchedGridCoords: List<Pair<Int, Int>> = emptyList(),
    val pairs: List<OpticalPair> = emptyList(),
    val globalMotionX: Double = 0.0,
    val globalMotionY: Double = 0.0,
    val globalMotionMagnitude: Double = 0.0,
    val correctedDispMedian: Double = 0.0,
    val correctedDispMAD: Double = 0.0,
    val correctedDispP90: Double = 0.0,
    val correctedDispMax: Double = 0.0,
    val opticalCenterX: Double = 0.0,
    val opticalCenterY: Double = 0.0,
    val opticalCenterConditionNumber: Double = 0.0,
    val opticalCenterValid: Boolean = false,
    val opticalCenterConfidence: Double = 0.0,
    
    val stabilityL1Std: Double = 0.0,
    val stabilityL2Std: Double = 0.0,
    val stabilityIsoStd: Double = 0.0,
    val stabilityAnisoStd: Double = 0.0,
    val measurementQualityPass: Boolean = false,
    val qualityMessage: String = "",
    val robustInliersCount: Int = 0,
    val weightedFitRms: Double = 0.0,
    val tensorA11: Double = 0.0,
    val tensorA12: Double = 0.0,
    val tensorA21: Double = 0.0,
    val tensorA22: Double = 0.0,
    val antisymmetricMag: Double = 0.0,
    val axisConfidence: Double = 0.0
)

data class V4Result(
    val success: Boolean,
    val errorMessage: String = "",
    val measurementQualityPass: Boolean = false,
    val qualityMessage: String = "",
    val sphDisplay: String = "",
    val cylDisplay: String = "",
    val axisDisplay: String = "",
    val lambda1: Double = 0.0,
    val lambda2: Double = 0.0,
    val isotropic: Double = 0.0,
    val anisotropic: Double = 0.0,
    val lambda1Std: Double = 0.0,
    val lambda2Std: Double = 0.0,
    val isotropicStd: Double = 0.0,
    val anisotropicStd: Double = 0.0,
    val commonGridPointsAcrossRuns: Int = 0,
    val correspondenceConsistency: Double = 0.0,
    val centerStdPx: Double = 0.0,
    val tensorStd: Double = 0.0,
    val allRuns: List<V4RunResult> = emptyList(),
    val trackedDots: Int = 0,
    val registrationRms: Double = 0.0,
    val registrationInliers: Int = 0,
    val fieldFitRms: Double = 0.0,
    val refDotCount: Int = 0,
    val lensDotCount: Int = 0,
    val meanDx: Double = 0.0,
    val meanDy: Double = 0.0,
    val visualVectorMap: Bitmap? = null,
    val lastRunResult: V4RunResult? = null,
    val registrationModel: String = "",
    val registrationRotationDeg: Double = 0.0,
    val registrationTx: Double = 0.0,
    val registrationTy: Double = 0.0,
    val registrationScale: Double = 0.0,
    val axis: Double = 0.0,
    val experimentalPower: ExperimentalPowerEstimate? = null
)

private data class AggResult(
    val success: Boolean,
    val errorMessage: String,
    val points: List<Point>,
    val frames: Int,
    val accepted: Int,
    val rejected: Int,
    val stableTrackCount: Int = 0,
    val medianTrackLifetime: Double = 0.0,
    val rejectedReferencePoints: List<Point> = emptyList(),
    val unmatchedLensPoints: List<Point> = emptyList(),
    val localOutlierRejections: Int = 0,
    val crossingVectorRejections: Int = 0,
    val medianLocalResidual: Double = 0.0,
    val madLocalResidual: Double = 0.0,
    val opticalRejectedReferencePoints: List<Point> = emptyList(),
    val opticalRejectedObservedPoints: List<Point> = emptyList(),
    val dispMedian: Double = 0.0,
    val dispMAD: Double = 0.0,
    val dispP90: Double = 0.0,
    val dispMax: Double = 0.0,
    val matchedGridCoords: List<Pair<Int, Int>> = emptyList(),
    val pairs: List<OpticalPair> = emptyList(),
    val globalMotionX: Double = 0.0,
    val globalMotionY: Double = 0.0,
    val globalMotionMagnitude: Double = 0.0,
    val correctedDispMedian: Double = 0.0,
    val correctedDispMAD: Double = 0.0,
    val correctedDispP90: Double = 0.0,
    val correctedDispMax: Double = 0.0,
    val opticalCenterX: Double = 0.0,
    val opticalCenterY: Double = 0.0,
    val opticalCenterConditionNumber: Double = 0.0,
    val opticalCenterValid: Boolean = false,
    val opticalCenterConfidence: Double = 0.0,
    val robustInliersCount: Int = 0,
    val weightedFitRms: Double = 0.0,
    val tensorA11: Double = 0.0,
    val tensorA12: Double = 0.0,
    val tensorA21: Double = 0.0,
    val tensorA22: Double = 0.0,
    val antisymmetricMag: Double = 0.0,
    val axisConfidence: Double = 0.0
)

object V4OpticalAnalyzer {

    private fun detectDots(bitmap: Bitmap): List<Point> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        
        val thresh = Mat()
        Imgproc.adaptiveThreshold(gray, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        val points = mutableListOf<Point>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 10.0 && area < 500.0) {
                val moments = Imgproc.moments(contour)
                if (moments.m00 != 0.0) {
                    val cx = moments.m10 / moments.m00
                    val cy = moments.m01 / moments.m00
                    points.add(Point(cx, cy))
                }
            }
        }
        
        mat.release()
        gray.release()
        thresh.release()
        hierarchy.release()
        return points
    }


    private fun estimateSimilarityTransform(srcPts: List<Point>, dstPts: List<Point>, ransacThresh: Double): Pair<Mat, Mat> {
        val srcMat = MatOfPoint2f().apply { fromList(srcPts) }
        val dstMat = MatOfPoint2f().apply { fromList(dstPts) }
        val mask = Mat()
        val partial = Calib3d.estimateAffinePartial2D(srcMat, dstMat, mask, Calib3d.RANSAC, ransacThresh)
        return Pair(partial, mask)
    }

    private fun aggregateFrames(frames: List<Bitmap>): AggResult {
        if (frames.isEmpty()) return AggResult(false, "No frames", emptyList(), 0, 0, 0)
        
        val allKeypoints = frames.map { detectDots(it) }
        val baseIdx = allKeypoints.indices.maxByOrNull { allKeypoints[it].size } ?: 0
        val basePoints = allKeypoints[baseIdx]
        
        if (basePoints.size < 10) return AggResult(false, "Not enough points in base frame", emptyList(), frames.size, 0, frames.size)
        
        val pointGroups = Array(basePoints.size) { mutableListOf<Point>() }
        
        var accepted = 0
        var rejected = 0
        
        for (i in frames.indices) {
            val pts = allKeypoints[i]
            if (pts.size < 10) {
                rejected++
                continue
            }
            
            val srcMat = MatOfPoint2f()
            val dstMat = MatOfPoint2f()
            
            val matchedBase = mutableListOf<Point>()
            val matchedCurr = mutableListOf<Point>()
            
            for (pt1 in basePoints) {
                var bestDist = Double.MAX_VALUE
                var bestPt2: Point? = null
                for (pt2 in pts) {
                    val dist = hypot(pt1.x - pt2.x, pt1.y - pt2.y)
                    if (dist < bestDist && dist < 100.0) {
                        bestDist = dist
                        bestPt2 = pt2
                    }
                }
                if (bestPt2 != null) {
                    matchedBase.add(pt1)
                    matchedCurr.add(bestPt2)
                }
            }
            
            if (matchedBase.size < 10) {
                rejected++
                continue
            }
            
            srcMat.fromList(matchedCurr)
            dstMat.fromList(matchedBase)
            
            val (transform, mask) = estimateSimilarityTransform(matchedCurr, matchedBase, 3.0)
            
            if (transform.empty()) {
                rejected++
                continue
            }
            
            val maskArray = ByteArray(mask.rows() * mask.cols())
            mask.get(0, 0, maskArray)
            val inliers = maskArray.count { it.toInt() != 0 }
            if (inliers < 10) {
                rejected++
                continue
            }
            
            val allPtsMat = MatOfPoint2f()
            allPtsMat.fromList(pts)
            val transformedPtsMat = MatOfPoint2f()
            Core.transform(allPtsMat, transformedPtsMat, transform)
            val transformedPts = transformedPtsMat.toList()
            
            accepted++
            
            for (pt in transformedPts) {
                var bestDist = Double.MAX_VALUE
                var bestIdx = -1
                for (j in basePoints.indices) {
                    val bPt = basePoints[j]
                    val dist = hypot(pt.x - bPt.x, pt.y - bPt.y)
                    if (dist < bestDist && dist < 5.0) {
                        bestDist = dist
                        bestIdx = j
                    }
                }
                if (bestIdx != -1) {
                    pointGroups[bestIdx].add(pt)
                }
            }
        }
        
        val finalPoints = mutableListOf<Point>()
        val trackLifetimes = mutableListOf<Double>()
        var stableCount = 0
        
        for (group in pointGroups) {
            val lifetime = group.size.toDouble() / max(1, frames.size)
            if (group.size > 0) {
                trackLifetimes.add(lifetime)
            }
            
            if (group.size > frames.size * 0.3) {
                stableCount++
                group.sortBy { it.x }
                val medianX = group[group.size / 2].x
                group.sortBy { it.y }
                val medianY = group[group.size / 2].y
                
                val madX = group.map { abs(it.x - medianX) }.sorted()[group.size / 2] * 1.4826
                val madY = group.map { abs(it.y - medianY) }.sorted()[group.size / 2] * 1.4826
                
                var sumX = 0.0
                var sumY = 0.0
                var count = 0
                for (pt in group) {
                    if (abs(pt.x - medianX) <= 3 * madX && abs(pt.y - medianY) <= 3 * madY) {
                        sumX += pt.x
                        sumY += pt.y
                        count++
                    }
                }
                if (count > 0) {
                    finalPoints.add(Point(sumX / count, sumY / count))
                }
            }
        }
        
        trackLifetimes.sort()
        val medianLifetime = if (trackLifetimes.isNotEmpty()) trackLifetimes[trackLifetimes.size / 2] else 0.0
        
        return AggResult(true, "", finalPoints, frames.size, accepted, rejected, stableCount, medianLifetime)
    }
    
    private fun estimateSpacing(pts: List<Point>): Double {
        if (pts.size < 2) return 50.0
        val dists = mutableListOf<Double>()
        for (p1 in pts) {
            var minDist = Double.MAX_VALUE
            for (p2 in pts) {
                if (p1 === p2) continue
                val d = hypot(p1.x - p2.x, p1.y - p2.y)
                if (d < minDist) minDist = d
            }
            if (minDist != Double.MAX_VALUE) {
                dists.add(minDist)
            }
        }
        if (dists.isEmpty()) return 50.0
        dists.sort()
        return dists[dists.size / 2]
    }

    private fun assignGridTopology(points: List<Point>, spacing: Double, rejections: MutableMap<String, Int> = mutableMapOf()): Map<Pair<Int, Int>, Point> {
        if (points.isEmpty()) return emptyMap()

        val angles = mutableListOf<Double>()
        val edges = mutableListOf<Triple<Int, Int, Double>>()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val p1 = points[i]
                val p2 = points[j]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = hypot(dx, dy)
                if (dist > spacing * 0.7 && dist < spacing * 1.3) {
                    var a = Math.atan2(dy, dx)
                    while (a < 0) a += Math.PI / 2.0
                    while (a >= Math.PI / 2.0) a -= Math.PI / 2.0
                    angles.add(a)
                    edges.add(Triple(i, j, Math.atan2(dy, dx)))
                }
            }
        }

        val medianAngle = if (angles.isNotEmpty()) {
            angles.sort()
            angles[angles.size / 2]
        } else 0.0

        val cx = points.map { it.x }.average()
        val cy = points.map { it.y }.average()
        var bestSeedIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val d = hypot(points[i].x - cx, points[i].y - cy)
            if (d < bestDist) {
                bestDist = d
                bestSeedIdx = i
            }
        }

        val grid = mutableMapOf<Pair<Int, Int>, Point>()
        var assigned = 0
        var ambiguous = 0
        var collisions = 0

        val queue = ArrayDeque<Pair<Int, Pair<Int, Int>>>()
        val visited = mutableSetOf<Int>()
        val coordsToIdx = mutableMapOf<Pair<Int, Int>, Int>()
        
        if (bestSeedIdx != -1) {
            queue.addLast(Pair(bestSeedIdx, Pair(0, 0)))
            visited.add(bestSeedIdx)
            
            while (queue.isNotEmpty()) {
                val (currIdx, coord) = queue.removeFirst()
                val (row, col) = coord
                
                if (grid.containsKey(coord)) {
                    collisions++
                    ambiguous++
                    continue
                }
                
                grid[coord] = points[currIdx]
                coordsToIdx[coord] = currIdx
                assigned++
                
                // Find neighbors
                for (edge in edges) {
                    if (edge.first == currIdx || edge.second == currIdx) {
                        val nIdx = if (edge.first == currIdx) edge.second else edge.first
                        if (visited.contains(nIdx)) continue
                        
                        val nPt = points[nIdx]
                        val cPt = points[currIdx]
                        val dx = nPt.x - cPt.x
                        val dy = nPt.y - cPt.y
                        
                        // Transform delta to aligned space
                        val cosA = Math.cos(-medianAngle)
                        val sinA = Math.sin(-medianAngle)
                        val rx = dx * cosA - dy * sinA
                        val ry = dx * sinA + dy * cosA
                        
                        var dr = 0
                        var dc = 0
                        if (abs(rx) > abs(ry)) {
                            dc = if (rx > 0) 1 else -1
                        } else {
                            dr = if (ry > 0) 1 else -1
                        }
                        
                        val nCoord = Pair(row + dr, col + dc)
                        queue.addLast(Pair(nIdx, nCoord))
                        visited.add(nIdx)
                    }
                }
            }
        }
        
        rejections["topologyInputDots"] = points.size
        rejections["topologyAssignedDots"] = assigned
        rejections["topologyUnassignedDots"] = points.size - assigned
        rejections["topologyCollisions"] = collisions
        rejections["topologyLargestComponent"] = assigned
        rejections["topologyConsistencyErrors"] = ambiguous
        
        return grid
    }

    suspend fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>): V4RunResult = withContext(Dispatchers.Default) {
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return@withContext V4RunResult(success = false, errorMessage = "Missing frames")
        }
        
        try {
            val refAgg = aggregateFrames(noLensFrames)
            if (!refAgg.success) {
                return@withContext V4RunResult(success = false, errorMessage = "Ref aggregation failed: ${refAgg.errorMessage}")
            }
            val baseRefPoints = refAgg.points
            
            val lensAgg = aggregateFrames(withLensFrames)
            if (!lensAgg.success) {
                return@withContext V4RunResult(success = false, errorMessage = "Lens aggregation failed: ${lensAgg.errorMessage}")
            }
            val baseLensPoints = lensAgg.points
            
            val spacing = estimateSpacing(baseRefPoints)
            
            val rejections = mutableMapOf(
                "geometric_gate" to 0,
                "distance_gate" to 0,
                "ransac_rejection" to 0,
                "roi_rejection" to 0,
                "duplicate_match" to 0,
                "registration_inconsistency" to 0,
                "low_confidence" to 0,
                "other" to 0
            )
            
            val w = noLensFrames[0].width.toDouble()
            val h = noLensFrames[0].height.toDouble()
            
            val gridMap = assignGridTopology(baseRefPoints, spacing, rejections)

            val binSize = spacing * 0.2
            val histogram = HashMap<Pair<Int, Int>, Int>()
            for (ptRef in baseRefPoints) {
                for (ptLens in baseLensPoints) {
                    val dx = ptLens.x - ptRef.x
                    val dy = ptLens.y - ptRef.y
                    if (hypot(dx, dy) < spacing * 4.0) {
                        val binX = (dx / binSize).roundToInt()
                        val binY = (dy / binSize).roundToInt()
                        val key = Pair(binX, binY)
                        histogram[key] = histogram.getOrDefault(key, 0) + 1
                    }
                }
            }
            
            var bestBin = Pair(0, 0)
            var maxCount = 0
            for ((bin, count) in histogram) {
                if (count > maxCount) {
                    maxCount = count
                    bestBin = bin
                }
            }
            

            val globalTx = bestBin.first * binSize
            val globalTy = bestBin.second * binSize

            // TASK 2 - USE MUTUAL NEAREST-NEIGHBOR SEEDING
            val acceptedMatches = mutableMapOf<Pair<Int, Int>, Point>()
            var nonMutualCount = 0
            var seedDistanceRejects = 0

            for ((coord, ptRef) in gridMap) {
                val shiftedX = ptRef.x + globalTx
                val shiftedY = ptRef.y + globalTy
                
                var bestDistRefToLens = Double.MAX_VALUE
                var bestLens: Point? = null
                for (ptLens in baseLensPoints) {
                    val dist = hypot(ptLens.x - shiftedX, ptLens.y - shiftedY)
                    if (dist < bestDistRefToLens) {
                        bestDistRefToLens = dist
                        bestLens = ptLens
                    }
                }
                
                if (bestLens != null && bestDistRefToLens < spacing * 0.4) {
                    // Check mutual nearest-neighbor
                    var bestDistLensToRef = Double.MAX_VALUE
                    var bestRefCoord: Pair<Int, Int>? = null
                    for ((c, rPt) in gridMap) {
                        val sX = rPt.x + globalTx
                        val sY = rPt.y + globalTy
                        val d = hypot(bestLens.x - sX, bestLens.y - sY)
                        if (d < bestDistLensToRef) {
                            bestDistLensToRef = d
                            bestRefCoord = c
                        }
                    }
                    if (bestRefCoord == coord) {
                        acceptedMatches[coord] = bestLens
                    } else {
                        nonMutualCount++
                    }
                } else if (bestLens != null) {
                    seedDistanceRejects++
                }
            }
            rejections["seedMutualMatches"] = acceptedMatches.size
            rejections["seed_distance"] = seedDistanceRejects
            rejections["non_mutual"] = nonMutualCount

            // TASK 3 - KEEP EXPANSION FLEXIBLE
            // Stage A: Topological neighbor expansion
            var changed = true
            while (changed) {
                changed = false
                for ((coord, ptRef) in gridMap) {
                    if (acceptedMatches.containsKey(coord)) continue
                    
                    val (r, c) = coord
                    val neighbors = listOf(Pair(r-1, c), Pair(r+1, c), Pair(r, c-1), Pair(r, c+1),
                                           Pair(r-1, c-1), Pair(r-1, c+1), Pair(r+1, c-1), Pair(r+1, c+1))
                    val matchedNeighbors = neighbors.filter { acceptedMatches.containsKey(it) }
                    
                    if (matchedNeighbors.isNotEmpty()) {
                        var sumDx = 0.0
                        var sumDy = 0.0
                        for (nCoord in matchedNeighbors) {
                            val nRef = gridMap[nCoord]!!
                            val nLens = acceptedMatches[nCoord]!!
                            sumDx += (nLens.x - nRef.x)
                            sumDy += (nLens.y - nRef.y)
                        }
                        val avgDx = sumDx / matchedNeighbors.size
                        val avgDy = sumDy / matchedNeighbors.size
                        
                        val predX = ptRef.x + avgDx
                        val predY = ptRef.y + avgDy
                        
                        var bestDist = Double.MAX_VALUE
                        var bestLens: Point? = null
                        for (ptLens in baseLensPoints) {
                            if (acceptedMatches.containsValue(ptLens)) continue
                            val dist = hypot(ptLens.x - predX, ptLens.y - predY)
                            if (dist < bestDist) {
                                bestDist = dist
                                bestLens = ptLens
                            }
                        }
                        
                        if (bestLens != null && bestDist < spacing * 0.7) {
                            acceptedMatches[coord] = bestLens
                            changed = true
                        }
                    }
                }
            }
            rejections["neighborExpandedMatches"] = acceptedMatches.size

            // Stage B: Robust affine prediction expansion
            if (acceptedMatches.size >= 10) {
                val srcList = acceptedMatches.values.map { gridMap[acceptedMatches.entries.find { e -> e.value == it }!!.key]!! }
                val dstList = acceptedMatches.values.toList()
                val srcMat = MatOfPoint2f().apply { fromList(srcList) }
                val dstMat = MatOfPoint2f().apply { fromList(dstList) }
                val inliers = Mat()
                val affine = Calib3d.estimateAffine2D(srcMat, dstMat, inliers, Calib3d.RANSAC, spacing * 0.5)
                
                if (!affine.empty()) {
                    for ((coord, ptRef) in gridMap) {
                        if (acceptedMatches.containsKey(coord)) continue
                        
                        val ptMat = MatOfPoint2f(ptRef)
                        val predMat = MatOfPoint2f()
                        Core.transform(ptMat, predMat, affine)
                        val predX = predMat.toArray()[0].x
                        val predY = predMat.toArray()[0].y
                        
                        var bestDist = Double.MAX_VALUE
                        var bestLens: Point? = null
                        for (ptLens in baseLensPoints) {
                            if (acceptedMatches.containsValue(ptLens)) continue
                            val dist = hypot(ptLens.x - predX, ptLens.y - predY)
                            if (dist < bestDist) {
                                bestDist = dist
                                bestLens = ptLens
                            }
                        }
                        
                        if (bestLens != null && bestDist < spacing * 0.8) {
                            acceptedMatches[coord] = bestLens
                        }
                    }
                }
            }
            rejections["affineExpandedMatches"] = acceptedMatches.size

            // TASK 4 - GLOBAL ONE-TO-ONE ASSIGNMENT
            val finalMatches = mutableMapOf<Pair<Int, Int>, Point>()
            var assignmentConflicts = 0
            val usedLensPts = mutableSetOf<Point>()
            
            // Build proposals based on existing acceptedMatches (these were accumulated iteratively)
            // But wait, the prompt says:
            // "After prediction, do not greedily assign dots one by one.
            // Build a cost matrix between predicted reference positions and available lens dots... Use globally sorted minimum-cost assignment with conflict resolution"
            // To do this, we can take the affine model (if valid) or local neighbor predictions, predict ALL grid points, and do a global match.
            // Actually, let's just collect ALL predicted positions for ALL grid map points based on affine or globalTx.
            
            val predictedPositions = mutableMapOf<Pair<Int, Int>, Point>()
            
            // Re-estimate affine with all current accepted matches to predict all points
            val affineGlobal = if (acceptedMatches.size >= 10) {
                val sList = acceptedMatches.values.map { gridMap[acceptedMatches.entries.find { e -> e.value == it }!!.key]!! }
                val dList = acceptedMatches.values.toList()
                val sMat = MatOfPoint2f().apply { fromList(sList) }
                val dMat = MatOfPoint2f().apply { fromList(dList) }
                Calib3d.estimateAffine2D(sMat, dMat, Mat(), Calib3d.RANSAC, spacing * 0.5)
            } else Mat()

            for ((coord, ptRef) in gridMap) {
                if (!affineGlobal.empty()) {
                    val ptMat = MatOfPoint2f(ptRef)
                    val predMat = MatOfPoint2f()
                    Core.transform(ptMat, predMat, affineGlobal)
                    predictedPositions[coord] = Point(predMat.toArray()[0].x, predMat.toArray()[0].y)
                } else {
                    predictedPositions[coord] = Point(ptRef.x + globalTx, ptRef.y + globalTy)
                }
            }

            // Create list of all (Coord, LensPoint, Distance)
            val proposals = mutableListOf<Triple<Pair<Int, Int>, Point, Double>>()
            for ((coord, predPt) in predictedPositions) {
                for (ptLens in baseLensPoints) {
                    val dist = hypot(ptLens.x - predPt.x, ptLens.y - predPt.y)
                    if (dist < spacing * 0.8) {
                        proposals.add(Triple(coord, ptLens, dist))
                    }
                }
            }
            
            // Sort by distance (cost)
            proposals.sortBy { it.third }
            
            for (proposal in proposals) {
                val coord = proposal.first
                val lensPt = proposal.second
                if (!finalMatches.containsKey(coord) && !usedLensPts.contains(lensPt)) {
                    finalMatches[coord] = lensPt
                    usedLensPts.add(lensPt)
                } else {
                    assignmentConflicts++
                }
            }
            rejections["assignment_conflict"] = assignmentConflicts
            
            acceptedMatches.clear()
            acceptedMatches.putAll(finalMatches)
            rejections["finalMatches"] = acceptedMatches.size

            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()
            val candidateCoords = mutableListOf<Pair<Int, Int>>()

            val rejectedRefs = mutableListOf<Point>()
            for ((coord, ptRef) in gridMap) {
                if (acceptedMatches.containsKey(coord)) {
                    candidateRef.add(ptRef)
                    candidateLens.add(acceptedMatches[coord]!!)
                    candidateCoords.add(coord)
                } else {
                    rejectedRefs.add(ptRef)
                    rejections["topology_rejection"] = rejections.getOrDefault("topology_rejection", 0) + 1
                }
            }
            
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
                    val fRes = analyzePoints(fRef, fLens, w, h, baseRefPoints.size, fPts.size, spacing, mutableMapOf(), emptyList())
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
            val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections, candidateCoords)
            
            return@withContext res.copy(
                stabilityL1Std = l1Std,
                stabilityL2Std = l2Std,
                stabilityIsoStd = isoStd,
                stabilityAnisoStd = anisoStd,
                measurementQualityPass = stabilityPass && res.success,
                qualityMessage = stabilityMsg,
                framesCaptured = withLensFrames.size,
                framesAccepted = lensAgg.accepted,
                framesRejected = lensAgg.rejected,
                temporalTrackCount = lensAgg.points.size,
                stableTrackCount = lensAgg.stableTrackCount,
                medianTrackLifetime = lensAgg.medianTrackLifetime,
                rejectedReferencePoints = rejectedRefs,
                unmatchedLensPoints = unmatchedLens
            )
            
        } catch (e: Exception) {
            Log.e("V4OpticalAnalyzer", "Analyze failed", e)
            return@withContext V4RunResult(success = false, errorMessage = "Exception: ${e.message}")
        }
    }
    fun analyzePoints(matchedRef: List<Point>, matchedLens: List<Point>, w: Double, h: Double, baseRefDotCount: Int = 0, baseLensDotCount: Int = 0, spacing: Double = 30.0, rejections: MutableMap<String, Int> = mutableMapOf(), matchedGridCoords: List<Pair<Int, Int>> = emptyList()): V4RunResult {
        try {
            val cx = w / 2.0
            val cy = h / 2.0
            val rMax = min(w, h) / 2.0
            
            val anchorRef = mutableListOf<Point>()
            val anchorLens = mutableListOf<Point>()
            val measurementRef = mutableListOf<Point>()
            val measurementLens = mutableListOf<Point>()
            
            val innerRadiusSq = (rMax * 0.7) * (rMax * 0.7)
            
            for (i in matchedRef.indices) {
                val pt = matchedRef[i]
                val dSq = (pt.x - cx) * (pt.x - cx) + (pt.y - cy) * (pt.y - cy)
                if (dSq > innerRadiusSq) {
                    anchorRef.add(matchedRef[i])
                    anchorLens.add(matchedLens[i])
                } else {
                    measurementRef.add(matchedRef[i])
                    measurementLens.add(matchedLens[i])
                }
            }
            
            var globalScaleAmbiguous = false
            var registrationRms = 0.0
            var inliersCount = 0
            
            val useRigidFallback = anchorRef.size < 4
            
            val regSrc: List<Point>
            val regDst: List<Point>
            var modelName = "Affine_OuterAnchors"
            var fallbackTriggered = false
            if (useRigidFallback) {
                regSrc = matchedLens
                regDst = matchedRef
                modelName = "SIMILARITY_FALLBACK_UNTRUSTED"
                fallbackTriggered = true
            } else {
                regSrc = anchorLens
                regDst = anchorRef
            }
            
            val ransacThresh = if (useRigidFallback) max(15.0, spacing * 0.8) else max(5.0, spacing * 0.4)
            
            if (regSrc.size < 4) {
                return V4RunResult(success = false, errorMessage = "REGISTRATION UNSTABLE (Not enough points)", candidateMatches = matchedRef.size, matchRejections = rejections)
            }
            
            val (transformMat, mask) = estimateSimilarityTransform(regSrc, regDst, ransacThresh)
            
            if (transformMat.empty()) {
                rejections["registration_inconsistency"] = rejections.getOrDefault("registration_inconsistency", 0) + matchedRef.size
                return V4RunResult(success = false, errorMessage = "Registration failed", candidateMatches = matchedRef.size, matchRejections = rejections)
            }
            
            val r00 = transformMat.get(0, 0)[0]
            val r10 = transformMat.get(1, 0)[0]
            val registrationScale = Math.hypot(r00, r10)
            val registrationRotationDeg = atan2(r10, r00) * 180.0 / Math.PI
            val registrationTx = transformMat.get(0, 2)[0]
            val registrationTy = transformMat.get(1, 2)[0]
            val registrationModel = if (useRigidFallback) "SIMILARITY_FALLBACK" else "SIMILARITY_ANCHOR"
            
            val maskArray = ByteArray(mask.rows() * mask.cols())
            mask.get(0, 0, maskArray)
            inliersCount = maskArray.count { it.toInt() != 0 }
            
            val ptsToMeasureRef = matchedRef.toMutableList()
                        val ptsToMeasureLens = matchedLens.toMutableList()
            
            val srcMeasMat = MatOfPoint2f()
            srcMeasMat.fromList(ptsToMeasureLens)
            val dstMeasMat = MatOfPoint2f()
            
            Core.transform(srcMeasMat, dstMeasMat, transformMat)
            val transformedLens = dstMeasMat.toList()
            
            if (!useRigidFallback) {
                var rSum = 0.0
                val anchorLensMat = MatOfPoint2f().apply { fromList(anchorLens) }
                val anchorLensTransformed = MatOfPoint2f()
                Core.transform(anchorLensMat, anchorLensTransformed, transformMat)
                val transformedAnchors = anchorLensTransformed.toList()
                for (i in anchorRef.indices) {
                    if (maskArray[i].toInt() != 0) {
                        val dx = transformedAnchors[i].x - anchorRef[i].x
                        val dy = transformedAnchors[i].y - anchorRef[i].y
                        rSum += dx * dx + dy * dy
                    }
                }
                registrationRms = sqrt(rSum / max(1, inliersCount))
            } else {
                var rSum = 0.0
                for (i in matchedRef.indices) {
                    if (maskArray[i].toInt() != 0) {
                        val dx = transformedLens[i].x - matchedRef[i].x
                        val dy = transformedLens[i].y - matchedRef[i].y
                        rSum += dx * dx + dy * dy
                    }
                }
                registrationRms = sqrt(rSum / max(1, inliersCount))
            }

            val opticalPairs = mutableListOf<OpticalPair>()
            for (i in matchedRef.indices) {
                opticalPairs.add(OpticalPair(
                    reference = matchedRef[i],
                    observed = transformedLens[i],
                    displacement = Point(transformedLens[i].x - matchedRef[i].x, transformedLens[i].y - matchedRef[i].y),
                    originalIndex = i
                ))
            }

            val searchRadius = spacing * 1.8
            val minNeighbors = 3
            
            val localResiduals = mutableListOf<Double>()

            // Task 4: Global Displacement Magnitude stats
            val globalMagnitudes = opticalPairs.map { hypot(it.displacement.x, it.displacement.y) }.sorted()
            var dispMedian = 0.0
            var dispMAD = 0.0
            var dispP90 = 0.0
            var dispMax = 0.0
            if (globalMagnitudes.isNotEmpty()) {
                dispMedian = globalMagnitudes[globalMagnitudes.size / 2]
                dispMAD = globalMagnitudes.map { abs(it - dispMedian) }.sorted()[globalMagnitudes.size / 2]
                dispP90 = globalMagnitudes[(globalMagnitudes.size * 0.9).toInt().coerceAtMost(globalMagnitudes.size - 1)]
                dispMax = globalMagnitudes.last()
            }

            for (i in opticalPairs.indices) {
                val pair = opticalPairs[i]
                
                // TASK 4: Reject extreme global magnitude
                val mag = hypot(pair.displacement.x, pair.displacement.y)
                if (mag > max(dispMedian + 5.0 * dispMAD, spacing)) {
                    pair.status = "GLOBAL_OUTLIER"
                    continue
                }

                val neighborIndices = mutableListOf<Int>()
                for (j in opticalPairs.indices) {
                    if (i == j) continue
                    val nPair = opticalPairs[j]
                    val distSq = (pair.reference.x - nPair.reference.x).pow(2) + (pair.reference.y - nPair.reference.y).pow(2)
                    if (distSq < searchRadius * searchRadius) {
                        neighborIndices.add(j)
                    }
                }
                
                if (neighborIndices.size < minNeighbors) {
                    // Not enough neighbors to verify, but we don't reject by default
                    continue
                }

                val nDispsX = neighborIndices.map { opticalPairs[it].displacement.x }.sorted()
                val nDispsY = neighborIndices.map { opticalPairs[it].displacement.y }.sorted()
                
                val medX = nDispsX[nDispsX.size / 2]
                val medY = nDispsY[nDispsY.size / 2]
                
                val nDistToMed = neighborIndices.map { 
                    hypot(opticalPairs[it].displacement.x - medX, opticalPairs[it].displacement.y - medY)
                }.sorted()
                val mad = nDistToMed[nDistToMed.size / 2]
                
                val distToMed = hypot(pair.displacement.x - medX, pair.displacement.y - medY)
                localResiduals.add(distToMed)
                
                val thresh = max(mad * 4.0, spacing * 0.15)
                if (distToMed > thresh) {
                    pair.status = "LOCAL_OUTLIER"
                    continue
                }

                var crossing = false
                for (j in neighborIndices) {
                    val nPair = opticalPairs[j]
                    
                    val refDist = hypot(pair.reference.x - nPair.reference.x, pair.reference.y - nPair.reference.y)
                    val obsDist = hypot(pair.observed.x - nPair.observed.x, pair.observed.y - nPair.observed.y)
                    
                    if (obsDist < refDist * 0.3) {
                        crossing = true
                        break
                    }
                    
                    val refVecX = nPair.reference.x - pair.reference.x
                    val refVecY = nPair.reference.y - pair.reference.y
                    val dstVecX = nPair.observed.x - pair.observed.x
                    val dstVecY = nPair.observed.y - pair.observed.y
                    
                    val dot = refVecX * dstVecX + refVecY * dstVecY
                    if (dot < 0) { 
                        crossing = true
                        break
                    }
                    
                    val ratio = obsDist / max(1e-5, refDist)
                    if (ratio > 3.0) {
                        crossing = true
                        break
                    }
                }
                
                if (crossing) {
                    pair.status = "CROSSING_REJECTED"
                }
            }
            
            val medianLocalRes = if (localResiduals.isNotEmpty()) localResiduals.sorted()[localResiduals.size / 2] else 0.0
            val madLocalRes = if (localResiduals.isNotEmpty()) {
                val m = medianLocalRes
                localResiduals.map { abs(it - m) }.sorted()[localResiduals.size / 2]
            } else 0.0

            val retainedPairs = opticalPairs.filter { it.status == "RETAINED" }
            
            // Motion correction
            val dxs = retainedPairs.map { it.displacement.x }.sorted()
            val dys = retainedPairs.map { it.displacement.y }.sorted()
            val globalMotionX = if (dxs.isNotEmpty()) dxs[dxs.size / 2] else 0.0
            val globalMotionY = if (dys.isNotEmpty()) dys[dys.size / 2] else 0.0
            val globalMotionMagnitude = kotlin.math.hypot(globalMotionX, globalMotionY)
            
            for (pair in retainedPairs) {
                pair.correctedDisplacement = Point(pair.displacement.x - globalMotionX, pair.displacement.y - globalMotionY)
            }
            
            val correctedMags = retainedPairs.map { kotlin.math.hypot(it.correctedDisplacement.x, it.correctedDisplacement.y) }.sorted()
            var correctedDispMedian = 0.0
            var correctedDispMAD = 0.0
            var correctedDispP90 = 0.0
            var correctedDispMax = 0.0
            if (correctedMags.isNotEmpty()) {
                correctedDispMedian = correctedMags[correctedMags.size / 2]
                correctedDispMAD = correctedMags.map { kotlin.math.abs(it - correctedDispMedian) }.sorted()[correctedMags.size / 2]
                correctedDispP90 = correctedMags[(correctedMags.size * 0.9).toInt().coerceAtMost(correctedMags.size - 1)]
                correctedDispMax = correctedMags.last()
            }
            
            val numMeas = retainedPairs.size
            if (numMeas < 6) {
                 return V4RunResult(success = false, errorMessage = "Insufficient measurement points (<6)", candidateMatches = matchedRef.size, acceptedMatches = inliersCount, matchRejections = rejections)
            }
            
            val A = Mat(numMeas, 3, CvType.CV_64F)
            val B = Mat(numMeas, 2, CvType.CV_64F)
            
            for (i in 0 until numMeas) {
                A.put(i, 0, retainedPairs[i].reference.x)
                A.put(i, 1, retainedPairs[i].reference.y)
                A.put(i, 2, 1.0)
                
                B.put(i, 0, retainedPairs[i].correctedDisplacement.x)
                B.put(i, 1, retainedPairs[i].correctedDisplacement.y)
            }
            
            val J_matrix = Mat(3, 2, CvType.CV_64F)
            val weights = DoubleArray(numMeas) { 1.0 }
            val huberK = 1.345
            
            var j00 = 0.0; var j10 = 0.0; var j01 = 0.0; var j11 = 0.0
            var c0 = 0.0; var c1 = 0.0
            
            for (iter in 0 until 5) {
                val AW = Mat(numMeas, 3, CvType.CV_64F)
                val BW = Mat(numMeas, 2, CvType.CV_64F)
                for (i in 0 until numMeas) {
                    val w = sqrt(weights[i])
                    AW.put(i, 0, A.get(i, 0)[0] * w)
                    AW.put(i, 1, A.get(i, 1)[0] * w)
                    AW.put(i, 2, A.get(i, 2)[0] * w)
                    
                    BW.put(i, 0, B.get(i, 0)[0] * w)
                    BW.put(i, 1, B.get(i, 1)[0] * w)
                }
                
                try {
                    Core.solve(AW, BW, J_matrix, Core.DECOMP_SVD)
                } catch (e: Exception) {
                    return V4RunResult(success = false, errorMessage = "IRLS solve failed: ${e.message}", candidateMatches = matchedRef.size, matchRejections = rejections)
                }
                
                j00 = J_matrix.get(0, 0)[0]
                j10 = J_matrix.get(0, 1)[0]
                j01 = J_matrix.get(1, 0)[0]
                j11 = J_matrix.get(1, 1)[0]
                c0 = J_matrix.get(2, 0)[0]
                c1 = J_matrix.get(2, 1)[0]
                
                val errors = DoubleArray(numMeas)
                for (i in 0 until numMeas) {
                    val x = A.get(i, 0)[0]
                    val y = A.get(i, 1)[0]
                    val errX = (j00 * x + j01 * y + c0) - B.get(i, 0)[0]
                    val errY = (j10 * x + j11 * y + c1) - B.get(i, 1)[0]
                    errors[i] = sqrt(errX * errX + errY * errY)
                }
                
                val sortedErrors = errors.sorted()
                val medianErr = sortedErrors[sortedErrors.size / 2]
                val sigma = max(medianErr / 0.6745, 1e-5)
                
                for (i in 0 until numMeas) {
                    val r = errors[i] / sigma
                    weights[i] = if (r <= huberK) 1.0 else huberK / r
                }
            }
            
            for (i in 0 until numMeas) {
                retainedPairs[i].robustWeight = weights[i]
            }


            // Optical center calculation via inverse Jacobian
            val det = j00 * j11 - j01 * j10
            var opticalCenterX = 0.0
            var opticalCenterY = 0.0
            
            // Calculate condition number of J
            val j_trace_JtJ = j00*j00 + j01*j01 + j10*j10 + j11*j11
            val j_det_JtJ = det * det
            val j_sqrt = Math.sqrt(Math.max(0.0, j_trace_JtJ * j_trace_JtJ - 4 * j_det_JtJ))
            val j_eig1 = (j_trace_JtJ + j_sqrt) / 2.0
            val j_eig2 = (j_trace_JtJ - j_sqrt) / 2.0
            val opticalCenterConditionNumber = if (j_eig2 > 1e-15) Math.sqrt(j_eig1 / j_eig2) else Double.MAX_VALUE
            val opticalCenterConfidence = if (opticalCenterConditionNumber > 0) 1.0 / opticalCenterConditionNumber else 0.0
            val opticalCenterValid = opticalCenterConditionNumber < 20.0 && Math.abs(det) > 1e-8
            
            if (opticalCenterValid) {
                val invJ00 = j11 / det
                val invJ01 = -j01 / det
                val invJ10 = -j10 / det
                val invJ11 = j00 / det
                
                opticalCenterX = -(invJ00 * c0 + invJ01 * c1)
                opticalCenterY = -(invJ10 * c0 + invJ11 * c1)
            } else {
                opticalCenterX = Double.NaN
                opticalCenterY = Double.NaN
            }

            var opticalFieldRetainedCount = 0
            val finalPtsToMeasureRef = mutableListOf<Point>()
            var q1 = false; var q2 = false; var q3 = false; var q4 = false
            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            
            var fieldFitRmsSum = 0.0
            
            for (i in 0 until numMeas) {
                if (weights[i] > 0.5) {
                    opticalFieldRetainedCount++
                    val pt = retainedPairs[i].reference
                    finalPtsToMeasureRef.add(pt)
                    
                    if (pt.x < opticalCenterX && pt.y < opticalCenterY) q1 = true
                    if (pt.x >= opticalCenterX && pt.y < opticalCenterY) q2 = true
                    if (pt.x < opticalCenterX && pt.y >= opticalCenterY) q3 = true
                    if (pt.x >= opticalCenterX && pt.y >= opticalCenterY) q4 = true
                    
                    if (pt.x < minX) minX = pt.x
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.y > maxY) maxY = pt.y
                    
                    val x = pt.x
                    val y = pt.y
                    val u = j00 * x + j01 * y + c0
                    val v = j10 * x + j11 * y + c1
                    
                    val dx = retainedPairs[i].correctedDisplacement.x
                    val dy = retainedPairs[i].correctedDisplacement.y
                    
                    fieldFitRmsSum += weights[i] * ((u - dx) * (u - dx) + (v - dy) * (v - dy))
                }
            }
            
            val fieldFitRms = sqrt(fieldFitRmsSum / max(1, opticalFieldRetainedCount))
            
            val quadCount = (if (q1) 1 else 0) + (if (q2) 1 else 0) + (if (q3) 1 else 0) + (if (q4) 1 else 0)
            val spreadX = if (maxX > minX) maxX - minX else 0.0
            val spreadY = if (maxY > minY) maxY - minY else 0.0
            val spatialCoveragePct = ((spreadX * spreadY) / (w * h)) * 100.0
            
            if (opticalFieldRetainedCount < 20) {
                 return V4RunResult(success = false, errorMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE (Points < 20)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections)
            }
            
            if (quadCount < 3 || spatialCoveragePct < 40.0) {
                rejections["roi_rejection"] = rejections.getOrDefault("roi_rejection", 0) + opticalFieldRetainedCount
                return V4RunResult(success = false, errorMessage = "INSUFFICIENT SPATIAL CORRESPONDENCE (Quads: $quadCount, Cov: ${String.format("%.1f", spatialCoveragePct)}%)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }
            
            if (!opticalCenterValid) {
                return V4RunResult(success = false, errorMessage = "INVALID CENTER (ill-conditioned fit)", candidateMatches = matchedRef.size, acceptedMatches = opticalFieldRetainedCount, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }
            
            // Recompute SVDecomp for degeneracy check using just retained points
            val AW_final = Mat(opticalFieldRetainedCount, 3, CvType.CV_64F)
            for (i in 0 until opticalFieldRetainedCount) {
                AW_final.put(i, 0, finalPtsToMeasureRef[i].x)
                AW_final.put(i, 1, finalPtsToMeasureRef[i].y)
                AW_final.put(i, 2, 1.0)
            }
            val wMat = Mat()
            Core.SVDecomp(AW_final, wMat, Mat(), Mat())
            var rank = 0
            var cond = 0.0
            val sv = DoubleArray(wMat.rows())
            if (!wMat.empty()) {
                for (i in 0 until wMat.rows()) {
                    val s = wMat.get(i, 0)[0]
                    sv[i] = s
                    if (s > 1e-5) rank++
                }
                cond = if (sv.last() > 1e-9) sv.first() / sv.last() else Double.MAX_VALUE
            }
            
            var degeneracyStatus = "OK"
            if (rank < 3 || cond > 5000.0) {
                degeneracyStatus = "DEGENERATE (rank=$rank, cond=${String.format("%.1f", cond)})"
            }
            
            val symCross = (j01 + j10) / 2.0
            val antisymmetricMag = kotlin.math.abs(j01 - j10) / 2.0
            
            val J_sym = Mat(2, 2, CvType.CV_64F)
            J_sym.put(0, 0, j00)
            J_sym.put(0, 1, symCross)
            J_sym.put(1, 0, symCross)
            J_sym.put(1, 1, j11)
            
            val eValSym = Mat()
            val eVecSym = Mat()
            Core.eigen(J_sym, eValSym, eVecSym)
            
            var l1 = 0.0
            var l2 = 0.0
            var vx = 1.0
            var vy = 0.0
            
            if (eValSym.rows() >= 2) {
                l1 = eValSym.get(0, 0)[0]
                l2 = eValSym.get(1, 0)[0]
                
                if (kotlin.math.abs(l2) > kotlin.math.abs(l1)) {
                    val temp = l1
                    l1 = l2
                    l2 = temp
                    vx = eVecSym.get(1, 0)[0]
                    vy = eVecSym.get(1, 1)[0]
                } else {
                    vx = eVecSym.get(0, 0)[0]
                    vy = eVecSym.get(0, 1)[0]
                }
            }
            
            val iso = (l1 + l2) / 2.0
            val aniso = l1 - l2
            val axisConfidence = kotlin.math.abs(aniso) / kotlin.math.max(1e-5, antisymmetricMag + fieldFitRms/30.0)
            
            var axisDeg = kotlin.math.atan2(vy, vx) * 180.0 / Math.PI
            while (axisDeg < 0.0) axisDeg += 180.0
            while (axisDeg >= 180.0) axisDeg -= 180.0
            
            var sumDx = 0.0; var sumDy = 0.0
            for (i in 0 until numMeas) {
                sumDx += retainedPairs[i].correctedDisplacement.x
                sumDy += retainedPairs[i].correctedDisplacement.y
            }
            val meanDx = sumDx / numMeas
            val meanDy = sumDy / numMeas
            
            return V4RunResult(
                success = true,
                errorMessage = "OK",
                axis = axisDeg,
                lambda1 = l1,
                lambda2 = l2,
                isotropic = iso,
                anisotropic = aniso,
                trackedDots = numMeas,
                topologyMatchCount = matchedRef.size,
                registrationFeatureCount = if (useRigidFallback) matchedRef.size else anchorRef.size,
                registrationInliers = inliersCount,
                registrationRms = registrationRms,
                opticalFieldInputCount = numMeas,
                opticalFieldRetainedCount = opticalFieldRetainedCount,
                fieldFitRms = fieldFitRms,
                refDotCount = baseRefDotCount,
                lensDotCount = baseLensDotCount,
                meanDx = meanDx,
                meanDy = meanDy,
                referencePoints = opticalPairs.map { it.reference },
                observedPoints = opticalPairs.map { it.observed },
                localOutlierRejections = opticalPairs.count { it.status == "LOCAL_OUTLIER" },
                crossingVectorRejections = opticalPairs.count { it.status == "CROSSING_REJECTED" },
                medianLocalResidual = medianLocalRes,
                madLocalResidual = madLocalRes,
                opticalRejectedReferencePoints = opticalPairs.filter { it.status != "RETAINED" }.map { it.reference },
                opticalRejectedObservedPoints = opticalPairs.filter { it.status != "RETAINED" }.map { it.observed },
                dispMedian = dispMedian,
                dispMAD = dispMAD,
                dispP90 = dispP90,
                dispMax = dispMax,
                pairs = opticalPairs,
                globalMotionX = globalMotionX,
                globalMotionY = globalMotionY,
                globalMotionMagnitude = globalMotionMagnitude,
                correctedDispMedian = correctedDispMedian,
                correctedDispMAD = correctedDispMAD,
                correctedDispP90 = correctedDispP90,
                correctedDispMax = correctedDispMax,
                matchedGridCoords = matchedGridCoords,
                opticalCenterX = opticalCenterX,
                opticalCenterY = opticalCenterY,
                opticalCenterConditionNumber = opticalCenterConditionNumber,
                opticalCenterValid = opticalCenterValid,
                opticalCenterConfidence = opticalCenterConfidence,
                robustInliersCount = opticalFieldRetainedCount,
                weightedFitRms = fieldFitRms,
                tensorA11 = j00,
                tensorA12 = j01,
                tensorA21 = j10,
                tensorA22 = j11,
                antisymmetricMag = antisymmetricMag,
                axisConfidence = axisConfidence,
                refWidth = w.toInt(),
                refHeight = h.toInt(),
                registrationModel = registrationModel,
                registrationRotationDeg = registrationRotationDeg,
                registrationTx = registrationTx,
                registrationTy = registrationTy,
                registrationScale = registrationScale,
                candidateMatches = matchedRef.size,
                acceptedMatches = opticalFieldRetainedCount,
                matchRejections = rejections,
                spatialCoveragePct = spatialCoveragePct,
                quadrantCoverage = quadCount,
                matrixRank = rank,
                conditionNumber = cond,
                degeneracyStatus = degeneracyStatus
            )
        } catch (e: Exception) {
            Log.e("V4OpticalAnalyzer", "AnalyzePoints failed", e)
            return V4RunResult(success = false, errorMessage = "Exception: ${e.message}")
        }
    }
    suspend fun calculateRepeatability(allRuns: List<V4RunResult>): V4Result = withContext(Dispatchers.Default) {
        val validRuns = allRuns.filter { it.success }
        if (validRuns.isEmpty()) {
            val errorMsg = allRuns.mapIndexed { i, r -> "Run ${i+1}: ${r.errorMessage}" }.joinToString("\n")
            return@withContext V4Result(
                success = false,
                errorMessage = errorMsg,
                measurementQualityPass = false,
                qualityMessage = "insufficient valid runs",
                allRuns = allRuns,
                lastRunResult = allRuns.lastOrNull()
            )
        }
        
        val lambda1Mean = validRuns.map { it.lambda1 }.average()
        val lambda2Mean = validRuns.map { it.lambda2 }.average()
        val isotropicMean = validRuns.map { it.isotropic }.average()
        val anisotropicMean = validRuns.map { it.anisotropic }.average()

        val lambda1Std = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.lambda1 - lambda1Mean) * (it.lambda1 - lambda1Mean) }.average()) else 0.0
        val lambda2Std = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.lambda2 - lambda2Mean) * (it.lambda2 - lambda2Mean) }.average()) else 0.0
        val isotropicStd = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.isotropic - isotropicMean) * (it.isotropic - isotropicMean) }.average()) else 0.0
        val anisotropicStd = if (validRuns.size > 1) kotlin.math.sqrt(validRuns.map { (it.anisotropic - anisotropicMean) * (it.anisotropic - anisotropicMean) }.average()) else 0.0

        val cvThreshold = 0.30
        val minSignal = 0.05
        
        fun checkCv(mean: Double, std: Double, name: String): String? {
            if (Math.abs(mean) > minSignal && validRuns.size > 1) {
                val cv = std / Math.abs(mean)
                if (cv > cvThreshold) return "$name CV=${String.format("%.2f", cv)}"
            }
            return null
        }
        
        val fails = listOfNotNull(
            checkCv(lambda1Mean, lambda1Std, "L1"),
            checkCv(lambda2Mean, lambda2Std, "L2"),
            checkCv(isotropicMean, isotropicStd, "ISO"),
            checkCv(anisotropicMean, anisotropicStd, "ANISO")
        )
        
        val l1_vals = validRuns.map { it.lambda1 }.sorted()
        val l2_vals = validRuns.map { it.lambda2 }.sorted()
        val iso_vals = validRuns.map { it.isotropic }.sorted()
        val aniso_vals = validRuns.map { it.anisotropic }.sorted()

        val lambda1Med = if (validRuns.size % 2 == 1) l1_vals[validRuns.size / 2] else (l1_vals[validRuns.size / 2 - 1] + l1_vals[validRuns.size / 2]) / 2.0
        val lambda2Med = if (validRuns.size % 2 == 1) l2_vals[validRuns.size / 2] else (l2_vals[validRuns.size / 2 - 1] + l2_vals[validRuns.size / 2]) / 2.0
        val isoMed = if (validRuns.size % 2 == 1) iso_vals[validRuns.size / 2] else (iso_vals[validRuns.size / 2 - 1] + iso_vals[validRuns.size / 2]) / 2.0
        val anisoMed = if (validRuns.size % 2 == 1) aniso_vals[validRuns.size / 2] else (aniso_vals[validRuns.size / 2 - 1] + aniso_vals[validRuns.size / 2]) / 2.0
        
        val qualPass = validRuns.size == 3 && fails.isEmpty()
        val qualMsg = if (validRuns.size < 3) "insufficient valid runs" else if (fails.isNotEmpty()) "Variance too high: ${fails.joinToString(", ")}" else "Pass"
        
        var sumSin = 0.0
        var sumCos = 0.0
        validRuns.forEach {
            val a = it.axis * 2.0
            sumSin += kotlin.math.sin(a)
            sumCos += kotlin.math.cos(a)
        }
        var axisOutput = if (validRuns.isNotEmpty()) kotlin.math.atan2(sumSin, sumCos) / 2.0 else 0.0
        if (axisOutput < 0) axisOutput += kotlin.math.PI
        
        return@withContext V4Result(
            success = true,
            measurementQualityPass = qualPass,
            qualityMessage = qualMsg,
            lambda1 = lambda1Med,
            lambda2 = lambda2Med,
            isotropic = isoMed,
            anisotropic = anisoMed,
            lambda1Std = lambda1Std,
            lambda2Std = lambda2Std,
            isotropicStd = isotropicStd,
            anisotropicStd = anisotropicStd,
            commonGridPointsAcrossRuns = validRuns.minOfOrNull { it.trackedDots } ?: 0,
            correspondenceConsistency = 1.0,
            centerStdPx = 0.0,
            tensorStd = 0.0,
            allRuns = allRuns,
            trackedDots = validRuns.map { it.trackedDots }.average().toInt(),
            registrationRms = validRuns.map { it.registrationRms }.average(),
            registrationInliers = validRuns.map { it.registrationInliers }.average().toInt(),
            fieldFitRms = validRuns.map { it.fieldFitRms }.average(),
            refDotCount = validRuns.first().refDotCount,
            lensDotCount = validRuns.map { it.lensDotCount }.average().toInt(),
            meanDx = validRuns.map { it.meanDx }.average(),
            meanDy = validRuns.map { it.meanDy }.average(),
            visualVectorMap = validRuns.lastOrNull()?.let { null },
            lastRunResult = validRuns.lastOrNull(),
            registrationModel = validRuns.lastOrNull()?.registrationModel ?: "",
            registrationRotationDeg = validRuns.map { it.registrationRotationDeg }.average(),
            registrationTx = validRuns.map { it.registrationTx }.average(),
            registrationTy = validRuns.map { it.registrationTy }.average(),
            registrationScale = validRuns.map { it.registrationScale }.average(),
            axis = axisOutput,
            experimentalPower = V4OpticalCalibration.estimatePower(lambda1Med, lambda2Med, axisOutput, qualPass, validRuns.map { it.fieldFitRms }.average())
        )
    }
    
    fun drawVectorMap(result: V4Result, mag: Float, useCorrectedVectors: Boolean = true): Bitmap? {
        if (result.lastRunResult == null) return null
        return drawVectorMapInternal(result.lastRunResult, mag, useCorrectedVectors)
    }
    
    private fun drawVectorMapInternal(run: V4RunResult, mag: Float, useCorrectedVectors: Boolean = true): Bitmap {
        if (run.refWidth <= 0 || run.refHeight <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(run.refWidth, run.refHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        
        val paintRef = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
        val paintLens = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL; isAntiAlias = true }
        val paintArrow = Paint().apply { color = Color.YELLOW; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
        
        val sizeRef = run.referencePoints.size
        val sizeLens = run.observedPoints.size
        val limit = min(sizeRef, sizeLens)
        
        val paintRejRef = Paint().apply { color = Color.argb(100, 255, 0, 0); style = Paint.Style.FILL; isAntiAlias = true }
        for (pt in run.rejectedReferencePoints) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 3f, paintRejRef)
        }
        
        val paintRejLens = Paint().apply { color = Color.argb(100, 0, 255, 0); style = Paint.Style.FILL; isAntiAlias = true }
        for (pt in run.unmatchedLensPoints) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 3f, paintRejLens)
        }
        
        val paintOptRejRef = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL; isAntiAlias = true }
        val paintOptRejLens = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
        val paintOptRejArrow = Paint().apply { color = Color.GRAY; strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true }

        
        val paintLocalRejArrow = Paint().apply { color = Color.argb(200, 255, 165, 0); strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true } // ORANGE
        val paintCrossRejArrow = Paint().apply { color = Color.MAGENTA; strokeWidth = 1.5f; style = Paint.Style.STROKE; isAntiAlias = true }
        
        for (pair in run.pairs) {
            val refPt = pair.reference
            val lensPt = pair.observed
            val dx = if (useCorrectedVectors) pair.correctedDisplacement.x * mag else pair.displacement.x * mag
            val dy = if (useCorrectedVectors) pair.correctedDisplacement.y * mag else pair.displacement.y * mag
            
            if (pair.status == "RETAINED") {
                if (pair.robustWeight < 0.5) {
                    val paintLowWeightRef = android.graphics.Paint().apply { color = android.graphics.Color.argb(100, 255, 0, 0); style = android.graphics.Paint.Style.FILL; isAntiAlias = true }
                    val paintLowWeightLens = android.graphics.Paint().apply { color = android.graphics.Color.argb(100, 0, 255, 0); style = android.graphics.Paint.Style.FILL; isAntiAlias = true }
                    val paintLowWeightArrow = android.graphics.Paint().apply { color = android.graphics.Color.argb(100, 255, 255, 0); strokeWidth = 1f; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
                    canvas.drawCircle(refPt.x.toFloat(), refPt.y.toFloat(), 3f, paintLowWeightRef)
                    canvas.drawCircle((refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), 3f, paintLowWeightLens)
                    canvas.drawLine(refPt.x.toFloat(), refPt.y.toFloat(), (refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), paintLowWeightArrow)
                } else {
                    canvas.drawCircle(refPt.x.toFloat(), refPt.y.toFloat(), 3f, paintRef)
                    canvas.drawCircle((refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), 3f, paintLens)
                    canvas.drawLine(refPt.x.toFloat(), refPt.y.toFloat(), (refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), paintArrow)
                }
            } else if (pair.status == "LOCAL_OUTLIER") {
                canvas.drawCircle(refPt.x.toFloat(), refPt.y.toFloat(), 3f, paintOptRejRef)
                canvas.drawCircle((refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), 3f, paintOptRejLens)
                canvas.drawLine(refPt.x.toFloat(), refPt.y.toFloat(), (refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), paintLocalRejArrow)
            } else { // CROSSING_REJECTED or GLOBAL_OUTLIER
                canvas.drawCircle(refPt.x.toFloat(), refPt.y.toFloat(), 3f, paintOptRejRef)
                canvas.drawCircle((refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), 3f, paintOptRejLens)
                canvas.drawLine(refPt.x.toFloat(), refPt.y.toFloat(), (refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), paintCrossRejArrow)
            }
        }

        
        return bitmap
    }
}
