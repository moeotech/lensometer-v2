package com.example.analysis.v6

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object V6DebugRenderer {

    fun renderDebugBitmap(
        width: Int, 
        height: Int, 
        refGrid: V6GridModel, 
        lensGrid: V6GridModel, 
        measurements: List<V6DirectionalMeasurement>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paintRef = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val paintLens = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
        }
        val paintMissing = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        for (pt in refGrid.points.values) {
            val pos = pt.observedPosition ?: pt.expectedPosition
            canvas.drawCircle(pos.x.toFloat(), pos.y.toFloat(), 5f, if (pt.isValid) paintRef else paintMissing)
        }

        for (pt in lensGrid.points.values) {
            if (pt.isValid) {
                val pos = pt.observedPosition!!
                canvas.drawCircle(pos.x.toFloat(), pos.y.toFloat(), 6f, paintLens)
            }
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
        }
        var y = 60f
        canvas.drawText("V6 STRUCTURED DEFLECTOMETRY", 20f, y, textPaint)
        y += 50f
        canvas.drawText("Ref cells: ${refGrid.validCellCount}", 20f, y, textPaint)
        y += 50f
        canvas.drawText("Lens cells: ${lensGrid.validCellCount}", 20f, y, textPaint)
        y += 50f
        canvas.drawText("Vectors: ${measurements.size}", 20f, y, textPaint)
        
        return bitmap
    }
}
