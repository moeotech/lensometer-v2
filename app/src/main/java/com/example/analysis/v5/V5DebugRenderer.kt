package com.example.analysis.v5

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import org.opencv.core.Point

object V5DebugRenderer {
    fun renderDebugBitmap(
        width: Int,
        height: Int,
        referencePoints: List<Point>,
        lensPoints: List<Point>,
        correspondences: List<V5Correspondence>,
        seedIndices: Set<Int>,
        telemetry: V5Telemetry? = null
    ): Bitmap {
        val w = if (width > 0) width else 800
        val h = if (height > 0) height else 800
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#121212")) // Dark background

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Draw reference points in cyan
        paint.color = Color.parseColor("#00E5FF")
        paint.strokeWidth = 6f
        for (pt in referencePoints) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5f, paint)
        }

        // Draw lens points in yellow
        paint.color = Color.parseColor("#FFD600")
        for (pt in lensPoints) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 4f, paint)
        }

        // Draw correspondences
        for (corr in correspondences) {
            if (corr.isInlier) {
                paint.color = if (seedIndices.contains(corr.referenceIndex)) {
                    Color.parseColor("#00E676") // Green for seeds
                } else {
                    Color.parseColor("#2979FF") // Blue/Cyan for accepted inliers
                }
                paint.strokeWidth = 2.5f
                canvas.drawLine(
                    corr.referencePoint.x.toFloat(), corr.referencePoint.y.toFloat(),
                    corr.observedPoint.x.toFloat(), corr.observedPoint.y.toFloat(),
                    paint
                )
                paint.color = Color.parseColor("#00E676")
                canvas.drawCircle(corr.observedPoint.x.toFloat(), corr.observedPoint.y.toFloat(), 4f, paint)
            } else {
                // Rejected / Outlier in thin red
                paint.color = Color.parseColor("#FF1744")
                paint.strokeWidth = 1f
                canvas.drawLine(
                    corr.referencePoint.x.toFloat(), corr.referencePoint.y.toFloat(),
                    corr.observedPoint.x.toFloat(), corr.observedPoint.y.toFloat(),
                    paint
                )
            }
        }

        // Draw HUD / Header text
        paint.color = Color.WHITE
        paint.textSize = 26f
        canvas.drawText("V5 Hardened Correspondence Debug View", 30f, 50f, paint)

        if (telemetry != null) {
            paint.textSize = 18f
            paint.color = Color.parseColor("#00E676")
            canvas.drawText("Accepted: ${telemetry.acceptedInlierMatches} | Seeds: ${telemetry.seedMatchCount}", 30f, 82f, paint)
            paint.color = Color.parseColor("#00E5FF")
            canvas.drawText("Median Res: ${String.format("%.1f", telemetry.medianResidualPx)}px | MAD: ${String.format("%.1f", telemetry.residualMadPx)}px", 30f, 110f, paint)
            paint.color = Color.parseColor("#FFD600")
            canvas.drawText("Max Res: ${String.format("%.1f", telemetry.maxAcceptedResidualPx)}px | RMS: ${String.format("%.2f", telemetry.transformRms)}px", 30f, 138f, paint)
        }

        return bitmap
    }
}
