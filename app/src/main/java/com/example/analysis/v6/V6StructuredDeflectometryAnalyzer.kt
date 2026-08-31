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

    private fun buildNeighborVectors(targetDef: V6TargetDefinition): List<V6NeighborVector> {
        val vectors = mutableListOf<V6NeighborVector>()
        val centerR = targetDef.centerRow
        val centerC = targetDef.centerColumn
        
        for (dr in -5..5) {
            for (dc in -5..5) {
                if (dr == 0 && dc == 0) continue
                vectors.add(V6NeighborVector(centerR, centerC, centerR + dr, centerC + dc))
            }
        }
        return vectors
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
        val targetDef = V6TargetDefinition()
        
        val refGrid = V6GridDetector.detectGrid(refPoints, targetDef)
        val lensGrid = V6GridDetector.detectGrid(lensPoints, targetDef)
        
        val neighborVectors = buildNeighborVectors(targetDef)
        val measurements = mutableListOf<V6DirectionalMeasurement>()
        
        for (vec in neighborVectors) {
            val pRefA = refGrid.points[Pair(vec.cellARow, vec.cellACol)]
            val pRefB = refGrid.points[Pair(vec.cellBRow, vec.cellBCol)]
            val pLensA = lensGrid.points[Pair(vec.cellARow, vec.cellACol)]
            val pLensB = lensGrid.points[Pair(vec.cellBRow, vec.cellBCol)]
            
            if (pRefA?.isValid == true && pRefB?.isValid == true && pLensA?.isValid == true && pLensB?.isValid == true) {
                val refAx = pRefA.observedPosition!!.x
                val refAy = pRefA.observedPosition!!.y
                val refBx = pRefB.observedPosition!!.x
                val refBy = pRefB.observedPosition!!.y
                
                val lensAx = pLensA.observedPosition!!.x
                val lensAy = pLensA.observedPosition!!.y
                val lensBx = pLensB.observedPosition!!.x
                val lensBy = pLensB.observedPosition!!.y
                
                val dxRef = refBx - refAx
                val dyRef = refBy - refAy
                val rRef = sqrt(dxRef * dxRef + dyRef * dyRef)
                
                val dxLens = lensBx - lensAx
                val dyLens = lensBy - lensAy
                val rLens = sqrt(dxLens * dxLens + dyLens * dyLens)
                
                val angle = Math.toDegrees(atan2(dyRef, dxRef))
                val ratio = if (rRef > 0) rLens / rRef else 1.0
                
                measurements.add(V6DirectionalMeasurement(angle, rRef, rLens, ratio))
            }
        }
        
        val sortedRatios = measurements.map { it.radiusRatio }.sorted()
        val medianRatio = if (sortedRatios.isNotEmpty()) sortedRatios[sortedRatios.size / 2] else 1.0
        val rangeRatio = if (sortedRatios.isNotEmpty()) sortedRatios.last() - sortedRatios.first() else 0.0
        val consistency = if (measurements.size > 20) "PASS" else "FAIL"
        
        val fitResult = V6SinusoidalFitter.fit(measurements)
        val prescription = V6PrescriptionResult(null, null, null, false)
        
        val telemetry = V6Telemetry(
            success = measurements.isNotEmpty(),
            failureReason = if (measurements.isEmpty()) "No valid neighbor vectors" else "",
            referenceValidCells = refGrid.validCellCount,
            lensValidCells = lensGrid.validCellCount,
            validDirectionalVectors = measurements.size,
            ratioMedian = medianRatio,
            ratioRange = rangeRatio,
            directionalConsistency = consistency,
            gitCommit = "HEAD",
            deviceGeometry = V6DeviceGeometry()
        )
        
        val debugBitmap = V6DebugRenderer.renderDebugBitmap(width, height, refGrid, lensGrid, measurements)
        
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
