package com.example.analysis.v6

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

data class V6Result(
    val telemetry: V6Telemetry,
    val refGrid: V6GridModel? = null,
    val lensGrid: V6GridModel? = null,
    val measurements: List<V6DirectionalMeasurement> = emptyList(),
    val fitResult: V6SinusoidalFitter.FitResult? = null,
    val prescription: V6PrescriptionResult? = null,
    val debugBitmap: Bitmap? = null
)

object V6StructuredDeflectometryAnalyzer {
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
            if (area <= 10.0 || area >= 500.0) continue
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = if (perimeter > 0.0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0
            if (circularity < 0.15) continue
            val moments = Imgproc.moments(contour)
            if (moments.m00 != 0.0) {
                val cx = moments.m10 / moments.m00
                val cy = moments.m01 / moments.m00
                points.add(Point(cx, cy))
            }
        }
        
        mat.release()
        gray.release()
        thresh.release()
        hierarchy.release()
        return points
    }

    suspend fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>): V6Result = withContext(Dispatchers.Default) {
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return@withContext V6Result(V6Telemetry(success = false, failureReason = "Missing frames"))
        }
        val refPoints = noLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()
        val lensPoints = withLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()
        if (refPoints.isEmpty() || lensPoints.isEmpty()) {
            return@withContext V6Result(V6Telemetry(success = false, failureReason = "No points detected"))
        }
        return@withContext analyzePoints(refPoints, lensPoints, noLensFrames[0].width, noLensFrames[0].height)
    }

    fun analyzePoints(refPoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920): V6Result {
        val refGrid = V6GridDetector.recoverZeroGrid(refPoints)
        val lensGrid = V6GridDetector.recoverLensGrid(lensPoints, refGrid)
        
        val commonKeys = refGrid.points.keys.intersect(lensGrid.points.keys)
        // Find anchor as the closest cell to (0,0) that exists in both grids
        val anchor = commonKeys.minByOrNull { hypot(it.first.toDouble(), it.second.toDouble()) }
        
        val measurements = mutableListOf<V6DirectionalMeasurement>()
        
        if (anchor != null) {
            // Add vectors from the robust central anchor to all other common cells
            for (rc in commonKeys) {
                if (rc != anchor) {
                    val pRefA = refGrid.points[anchor]!!
                    val pRefB = refGrid.points[rc]!!
                    val pLensA = lensGrid.points[anchor]!!
                    val pLensB = lensGrid.points[rc]!!
                    
                    val dxRef = pRefB.observedPosition!!.x - pRefA.observedPosition!!.x
                    val dyRef = pRefB.observedPosition!!.y - pRefA.observedPosition!!.y
                    val rRef = hypot(dxRef, dyRef)
                    
                    val dxLens = pLensB.observedPosition!!.x - pLensA.observedPosition!!.x
                    val dyLens = pLensB.observedPosition!!.y - pLensA.observedPosition!!.y
                    val rLens = hypot(dxLens, dyLens)
                    
                    val angle = Math.toDegrees(atan2(dyRef, dxRef))
                    val ratio = if (rRef > 0) rLens / rRef else 1.0
                    
                    measurements.add(V6DirectionalMeasurement(angle, rRef, rLens, ratio))
                }
            }
        }
        
        val sortedRatios = measurements.map { it.radiusRatio }.sorted()
        val medianRatio = if (sortedRatios.isNotEmpty()) sortedRatios[sortedRatios.size / 2] else 1.0
        val rangeRatio = if (sortedRatios.isNotEmpty()) sortedRatios.last() - sortedRatios.first() else 0.0
        val consistency = if (measurements.size >= 10) "PASS" else "FAIL"
        
        val fitResult = V6SinusoidalFitter.fit(measurements)
        val prescription = V6PrescriptionResult(null, null, null, false)
        
        val refAssignmentPct = if (refPoints.isNotEmpty()) (refGrid.validCellCount.toDouble() / refPoints.size) * 100.0 else 0.0
        val lensAssignmentPct = if (lensPoints.isNotEmpty()) (lensGrid.validCellCount.toDouble() / lensPoints.size) * 100.0 else 0.0
        
        val telemetry = V6Telemetry(
            success = measurements.isNotEmpty(),
            failureReason = if (measurements.isEmpty()) "No valid neighbor vectors" else "",
            detectedReferencePoints = refPoints.size,
            detectedLensPoints = lensPoints.size,
            estimatedGridAngleDeg = refGrid.angleDeg,
            estimatedSpacingX = refGrid.spacingX,
            estimatedSpacingY = refGrid.spacingY,
            gridOriginX = refGrid.originX,
            gridOriginY = refGrid.originY,
            referenceAssignedCells = refGrid.validCellCount,
            lensAssignedCells = lensGrid.validCellCount,
            commonGridCells = commonKeys.size,
            referenceAssignmentPct = refAssignmentPct,
            lensAssignmentPct = lensAssignmentPct,
            validDirectionalVectors = measurements.size,
            ratioMedian = medianRatio,
            ratioRange = rangeRatio,
            directionalConsistency = consistency,
            gitCommit = "HEAD",
            deviceGeometry = V6DeviceGeometry()
        )
        
        val debugBitmap = V6DebugRenderer.renderDebugBitmap(
            width, height, refPoints, lensPoints, refGrid, lensGrid, commonKeys
        )
        
        return V6Result(
            telemetry = telemetry,
            refGrid = refGrid,
            lensGrid = lensGrid,
            measurements = measurements,
            fitResult = fitResult,
            prescription = prescription,
            debugBitmap = debugBitmap
        )
    }
}
