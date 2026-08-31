package com.example.analysis.v5

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object V5DeflectometryAnalyzer {

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

    suspend fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>): V5MatchResult = withContext(Dispatchers.Default) {
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return@withContext V5MatchResult(
                success = false,
                errorMessage = "Missing frames for V5 analysis"
            )
        }

        val refPoints = noLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()
        val lensPoints = withLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()

        if (refPoints.isEmpty() || lensPoints.isEmpty()) {
            return@withContext V5MatchResult(
                success = false,
                errorMessage = "No points detected in reference or lens frames",
                referencePoints = refPoints,
                lensPoints = lensPoints
            )
        }

        return@withContext analyzePoints(refPoints, lensPoints, noLensFrames[0].width, noLensFrames[0].height)
    }

    fun analyzePoints(referencePoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920): V5MatchResult {
        val matcherOutput = V5GeometricMatcher.match(referencePoints, lensPoints)
        val correspondences = matcherOutput.correspondences
        val telemetry = matcherOutput.telemetry
        val seedIndices = matcherOutput.seedIndices

        val rawFieldResult = if (telemetry.success) {
            V5FieldAnalyzer.analyze(correspondences)
        } else null

        val debugBitmap = V5DebugRenderer.renderDebugBitmap(
            width = width,
            height = height,
            referencePoints = referencePoints,
            lensPoints = lensPoints,
            correspondences = correspondences,
            seedIndices = seedIndices,
            seedRejectedIndices = matcherOutput.seedRejectedIndices,
            rawSeedCorrespondences = matcherOutput.rawSeedCorrespondences,
            telemetry = telemetry
        )

        return V5MatchResult(
            success = telemetry.success,
            errorMessage = telemetry.failureReason,
            correspondences = correspondences,
            telemetry = telemetry,
            debugBitmap = debugBitmap,
            rawFieldResult = rawFieldResult,
            referencePoints = referencePoints,
            lensPoints = lensPoints
        )
    }
}
