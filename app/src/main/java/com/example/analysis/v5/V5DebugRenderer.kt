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
        seedIndices: Set<Int>
    ): Bitmap {
        val w = if (width > 0) width else 800
        val h = if (height > 0) height else 800
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#121212")) // Dark background

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Draw reference points in blue/cyan
        paint.color = Color.parseColor("#00E5FF")
        paint.strokeWidth = 6f
        for ((idx, pt) in referencePoints.withIndex()) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 6f, paint)
        }

        // Draw lens points in orange/yellow
        paint.color = Color.parseColor("#FFD600")
        for (pt in lensPoints) {
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5f, paint)
        }

        // Draw accepted correspondences as lines
        paint.strokeWidth = 2f
        for (corr in correspondences) {
            paint.color = if (seedIndices.contains(corr.referenceIndex)) {
                Color.parseColor("#00E676") // Green for seeds
            } else {
                Color.parseColor("#2979FF") // Blue for MNN/expanded matches
            }
            canvas.drawLine(
                corr.referencePoint.x.toFloat(), corr.referencePoint.y.toFloat(),
                corr.observedPoint.x.toFloat(), corr.observedPoint.y.toFloat(),
                paint
            )

            // Draw observed point marker
            paint.color = Color.parseColor("#FF1744")
            canvas.drawCircle(corr.observedPoint.x.toFloat(), corr.observedPoint.y.toFloat(), 4f, paint)
        }

        // Draw legend / header text
        paint.color = Color.WHITE
        paint.textSize = 28f
        canvas.drawText("V5 Geometric Correspondence Debug View", 30f, 50f, paint)
        paint.textSize = 20f
        paint.color = Color.parseColor("#00E676")
        canvas.drawText("Seeds: ${seedIndices.size} | Accepted Matches: ${correspondences.size}", 30f, 85f, paint)

        return bitmap
    }
}
