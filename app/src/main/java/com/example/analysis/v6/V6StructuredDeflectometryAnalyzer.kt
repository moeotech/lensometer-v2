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
        
        var roiSource = "FALLBACK"
        var roiCenterX = noLensFrames[0].width / 2.0
        var roiCenterY = noLensFrames[0].height / 2.0
        var roiInnerR = min(noLensFrames[0].width, noLensFrames[0].height) * 0.25
        var roiOuterR = min(noLensFrames[0].width, noLensFrames[0].height) * 0.40

        for (frame in withLensFrames) {
            val ell = com.example.ui.detectLensEllipse(frame)
            if (ell != null) {
                roiCenterX = ell.center.x
                roiCenterY = ell.center.y
                val minR = min(ell.size.width, ell.size.height) / 2.0
                val maxR = max(ell.size.width, ell.size.height) / 2.0
                roiInnerR = minR * 0.8
                roiOuterR = maxR * 1.2
                roiSource = "AUTO"
                break
            }
        }
        
        val providedRoi = V6LensRoi(roiCenterX, roiCenterY, roiInnerR, roiOuterR)
        return@withContext analyzePoints(refPoints, lensPoints, noLensFrames[0].width, noLensFrames[0].height, providedRoi, roiSource)
    }

    fun analyzePoints(refPoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920, providedRoi: V6LensRoi? = null, providedRoiSource: String = "FALLBACK"): V6Result {
        val refGrid = V6GridDetector.recoverZeroGrid(refPoints)
        val lensGrid = V6GridDetector.recoverLensGrid(lensPoints, refGrid)
        
        val roi = providedRoi ?: V6LensRoi(
            centerX = width / 2.0,
            centerY = height / 2.0,
            innerRadius = min(width, height) * 0.25,
            outerRadius = min(width, height) * 0.40
        )
        
        val commonKeys = refGrid.points.keys.intersect(lensGrid.points.keys)
        
        val outsideKeys = mutableListOf<Pair<Int, Int>>()
        val insideKeys = mutableListOf<Pair<Int, Int>>()
        val boundaryKeys = mutableListOf<Pair<Int, Int>>()
        
        var insideLensRefCells = 0
        var insideLensLensCells = 0

        for ((rc, pt) in refGrid.points) {
             if (roi.classify(pt.observedPosition!!) == V6CellRegion.INSIDE_LENS) insideLensRefCells++
        }
        for ((rc, pt) in lensGrid.points) {
             if (roi.classify(pt.observedPosition!!) == V6CellRegion.INSIDE_LENS) insideLensLensCells++
        }

        for (rc in commonKeys) {
            val pRef = refGrid.points[rc]!!.observedPosition!!
            val pLens = lensGrid.points[rc]!!.observedPosition!!
            val classRef = roi.classify(pRef)
            val classLens = roi.classify(pLens)
            
            if (classRef == V6CellRegion.OUTSIDE_LENS && classLens == V6CellRegion.OUTSIDE_LENS) {
                outsideKeys.add(rc)
            } else if (classRef == V6CellRegion.INSIDE_LENS && classLens == V6CellRegion.INSIDE_LENS) {
                insideKeys.add(rc)
            } else {
                boundaryKeys.add(rc)
            }
        }
        
        var globalTx = 0.0
        var globalTy = 0.0
        var globalRotation = 0.0
        var globalScale = 1.0

        val transformedRefGridPoints = mutableMapOf<Pair<Int, Int>, Point>()

        if (outsideKeys.size >= 3) {
            val srcPts = MatOfPoint2f(*outsideKeys.map { refGrid.points[it]!!.observedPosition!! }.toTypedArray())
            val dstPts = MatOfPoint2f(*outsideKeys.map { lensGrid.points[it]!!.observedPosition!! }.toTypedArray())
            
            val inliers = Mat()
            val affine = org.opencv.calib3d.Calib3d.estimateAffinePartial2D(srcPts, dstPts, inliers)
            
            if (!affine.empty()) {
                val a11 = affine.get(0, 0)[0]
                val a12 = affine.get(0, 1)[0]
                val tx = affine.get(0, 2)[0]
                val a21 = affine.get(1, 0)[0]
                val a22 = affine.get(1, 1)[0]
                val ty = affine.get(1, 2)[0]
                
                globalTx = tx
                globalTy = ty
                globalScale = sqrt(a11 * a11 + a21 * a21)
                globalRotation = atan2(a21, a11) * 180.0 / PI
                
                for ((rc, pt) in refGrid.points) {
                    val obs = pt.observedPosition!!
                    val nx = a11 * obs.x + a12 * obs.y + tx
                    val ny = a21 * obs.x + a22 * obs.y + ty
                    transformedRefGridPoints[rc] = Point(nx, ny)
                }
            } else {
                for ((rc, pt) in refGrid.points) transformedRefGridPoints[rc] = pt.observedPosition!!
            }
            inliers.release()
            affine.release()
        } else {
            for ((rc, pt) in refGrid.points) transformedRefGridPoints[rc] = pt.observedPosition!!
        }
        
        val neighborOffsets = listOf(
            Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1),
            Pair(2, 0), Pair(0, 2), Pair(2, 2), Pair(2, -2)
        )
        
        val rawMeasurements = mutableListOf<V6DirectionalMeasurement>()
        var rejectedGridMismatch = 0
        var rejectedBoundary = 0
        var rawVectorCount = 0

        for (rc in insideKeys) {
            val (r, c) = rc
            val pRefA = transformedRefGridPoints[rc]!!
            val pLensA = lensGrid.points[rc]!!.observedPosition!!
            
            for ((dr, dc) in neighborOffsets) {
                val neighborRc = Pair(r + dr, c + dc)
                if (lensGrid.points.containsKey(neighborRc) && refGrid.points.containsKey(neighborRc)) {
                    rawVectorCount++
                    
                    if (!insideKeys.contains(neighborRc)) {
                        rejectedBoundary++
                        continue
                    }
                    
                    val pRefB = transformedRefGridPoints[neighborRc]!!
                    val pLensB = lensGrid.points[neighborRc]!!.observedPosition!!
                    
                    val dxRef = pRefB.x - pRefA.x
                    val dyRef = pRefB.y - pRefA.y
                    val rRef = hypot(dxRef, dyRef)
                    
                    val dxLens = pLensB.x - pLensA.x
                    val dyLens = pLensB.y - pLensA.y
                    val rLens = hypot(dxLens, dyLens)
                    
                    val angle = Math.toDegrees(atan2(dyRef, dxRef))
                    val ratio = if (rRef > 0) rLens / rRef else 1.0
                    
                    rawMeasurements.add(V6DirectionalMeasurement(angle, rRef, rLens, ratio, Pair(dr, dc), pLensA))
                }
            }
        }
        
        val rawRatios = rawMeasurements.map { it.radiusRatio }.sorted()
        val rawRatioMedian = if (rawRatios.isNotEmpty()) rawRatios[rawRatios.size / 2] else 1.0
        
        val absDevs = rawRatios.map { abs(it - rawRatioMedian) }.sorted()
        val mad = if (absDevs.isNotEmpty()) absDevs[absDevs.size / 2] else 0.0
        val sigma = max(1e-4, mad / 0.6745)
        
        var rejectedRatioOutlier = 0
        val acceptedMeasurements = mutableListOf<V6DirectionalMeasurement>()
        
        for (m in rawMeasurements) {
            if (abs(m.radiusRatio - rawRatioMedian) > 3.0 * sigma) {
                rejectedRatioOutlier++
            } else {
                acceptedMeasurements.add(m)
            }
        }
        
        val accRatios = acceptedMeasurements.map { it.radiusRatio }.sorted()
        val acceptedRatioMedian = if (accRatios.isNotEmpty()) accRatios[accRatios.size / 2] else 1.0
        val accAbsDevs = accRatios.map { abs(it - acceptedRatioMedian) }.sorted()
        val acceptedRatioMAD = if (accAbsDevs.isNotEmpty()) accAbsDevs[accAbsDevs.size / 2] else 0.0
        val acceptedRatioP05 = if (accRatios.isNotEmpty()) accRatios[(accRatios.size * 0.05).toInt()] else 1.0
        val acceptedRatioP95 = if (accRatios.isNotEmpty()) accRatios[(accRatios.size * 0.95).toInt()] else 1.0

        val fitResult = V6SinusoidalFitter.fit(acceptedMeasurements)
        val prescription = V6PrescriptionResult(null, null, null, false)
        
        var consistency = "FAIL"
        var consistencyReason = "Insufficient valid vectors"
        
        if (fitResult != null) {
            val angularCovOK = fitResult.angularCoverage > 50.0
            val countOK = fitResult.sampleCount >= 20
            val madOK = fitResult.fitMad < 0.1
            
            if (countOK && angularCovOK && madOK) {
                consistency = "PASS"
                consistencyReason = "Good coverage and low residual"
            } else if (countOK) {
                consistency = "WARN"
                consistencyReason = "Coverage: ${fitResult.angularCoverage.toInt()}%, MAD: ${String.format("%.3f", fitResult.fitMad)}"
            } else {
                consistency = "FAIL"
                consistencyReason = "Count: ${fitResult.sampleCount} < 20"
            }
        }

        val refAssignmentPct = if (refPoints.isNotEmpty()) (refGrid.validCellCount.toDouble() / refPoints.size) * 100.0 else 0.0
        val lensAssignmentPct = if (lensPoints.isNotEmpty()) (lensGrid.validCellCount.toDouble() / lensPoints.size) * 100.0 else 0.0
        
        val telemetry = V6Telemetry(
            success = acceptedMeasurements.isNotEmpty(),
            failureReason = if (acceptedMeasurements.isEmpty()) "No accepted vectors" else "",
            detectedReferencePoints = refPoints.size,
            detectedLensPoints = lensPoints.size,
            estimatedGridAngleDeg = refGrid.angleDeg,
            estimatedSpacingX = refGrid.spacingX,
            estimatedSpacingY = refGrid.spacingY,
            gridOriginX = refGrid.originX,
            gridOriginY = refGrid.originY,
            lensRoiCenterX = roi.centerX,
            lensRoiCenterY = roi.centerY,
            lensRoiInnerRadius = roi.innerRadius,
            lensRoiOuterRadius = roi.outerRadius,
            lensRoiSource = providedRoiSource,
            referenceAssignedCells = refGrid.validCellCount,
            lensAssignedCells = lensGrid.validCellCount,
            commonGridCells = commonKeys.size,
            referenceAssignmentPct = refAssignmentPct,
            lensAssignmentPct = lensAssignmentPct,
            insideLensRefCells = insideLensRefCells,
            insideLensLensCells = insideLensLensCells,
            insideLensCommonCells = insideKeys.size,
            outsideLensRegistrationCells = outsideKeys.size,
            rawVectorCount = rawVectorCount,
            acceptedVectorCount = acceptedMeasurements.size,
            rejectedRatioOutlier = rejectedRatioOutlier,
            rejectedGridMismatch = rejectedGridMismatch,
            rejectedBoundary = rejectedBoundary,
            rejectedSpatialConsistency = 0,
            globalTx = globalTx,
            globalTy = globalTy,
            globalRotation = globalRotation,
            globalScale = globalScale,
            rawRatioMedian = rawRatioMedian,
            acceptedRatioMedian = acceptedRatioMedian,
            acceptedRatioMAD = acceptedRatioMAD,
            acceptedRatioP05 = acceptedRatioP05,
            acceptedRatioP95 = acceptedRatioP95,
            validDirectionalVectors = acceptedMeasurements.size,
            ratioMedian = acceptedRatioMedian,
            ratioRange = if (accRatios.isNotEmpty()) accRatios.last() - accRatios.first() else 0.0,
            directionalConsistency = consistency,
            directionalConsistencyReason = consistencyReason,
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
            measurements = acceptedMeasurements,
            fitResult = fitResult,
            prescription = prescription,
            debugBitmap = debugBitmap
        )
    }
}
