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

data class V4RunResult(
    val success: Boolean,
    val errorMessage: String = "",
    val axis: Double = 0.0,
    val lambda1: Double = 0.0,
    val lambda2: Double = 0.0,
    val isotropic: Double = 0.0,
    val anisotropic: Double = 0.0,
    val trackedDots: Int = 0,
    val registrationRms: Double = 0.0,
    val ransacInliers: Int = 0,
    val fieldFitRms: Double = 0.0,
    val refDotCount: Int = 0,
    val lensDotCount: Int = 0,
    val meanDx: Double = 0.0,
    val meanDy: Double = 0.0,
    val referencePoints: List<Point> = emptyList(),
    val observedPoints: List<Point> = emptyList(),
    val refWidth: Int = 0,
    val refHeight: Int = 0,
    val globalScaleAmbiguous: Boolean = false,
    val framesCaptured: Int = 0,
    val framesAccepted: Int = 0,
    val framesRejected: Int = 0,
    val candidateMatches: Int = 0,
    val acceptedMatches: Int = 0,
    val rejectedMatches: Int = 0,
    val matrixRank: Int = 0,
    val conditionNumber: Double = 0.0,
    val degeneracyStatus: String = "OK",
    val matchRejections: Map<String, Int> = emptyMap(),
    val spatialCoveragePct: Double = 0.0,
    val quadrantCoverage: Int = 0,
    val temporalTrackCount: Int = 0,
    val stableTrackCount: Int = 0,
    val medianTrackLifetime: Double = 0.0
)

data class V4Result(
    val success: Boolean,
    val errorMessage: String = "",
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
    val allRuns: List<V4RunResult> = emptyList(),
    val trackedDots: Int = 0,
    val registrationRms: Double = 0.0,
    val ransacInliers: Int = 0,
    val fieldFitRms: Double = 0.0,
    val refDotCount: Int = 0,
    val lensDotCount: Int = 0,
    val meanDx: Double = 0.0,
    val meanDy: Double = 0.0,
    val visualVectorMap: Bitmap? = null,
    val lastRunResult: V4RunResult? = null,
    val globalScaleAmbiguous: Boolean = false
)

private data class AggResult(
    val success: Boolean,
    val errorMessage: String,
    val points: List<Point>,
    val frames: Int,
    val accepted: Int,
    val rejected: Int,
    val stableTrackCount: Int = 0,
    val medianTrackLifetime: Double = 0.0
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
            
            val mask = Mat()
            val transform = Calib3d.estimateAffinePartial2D(srcMat, dstMat, mask, Calib3d.RANSAC, 3.0)
            
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
