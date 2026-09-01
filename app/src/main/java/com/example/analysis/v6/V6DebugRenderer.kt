package com.example.analysis.v6

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.opencv.core.Point

object V6DebugRenderer {
    fun renderDebugBitmap(
        width: Int, 
        height: Int, 
        rawRefPoints: List<Point>,
        rawLensPoints: List<Point>,
        refGrid: V6GridModel, 
        lensGrid: V6GridModel, 
        commonKeys: Set<Pair<Int, Int>>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)

        val paintRawRef = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f; style = Paint.Style.STROKE }
        val paintRawLens = Paint().apply { color = Color.GREEN; strokeWidth = 2f; style = Paint.Style.STROKE }
        val paintRefCell = Paint().apply { color = Color.WHITE; strokeWidth = 4f; style = Paint.Style.STROKE }
        val paintLensCell = Paint().apply { color = Color.YELLOW; strokeWidth = 4f; style = Paint.Style.STROKE }
        val paintCommonCell = Paint().apply { color = Color.CYAN; strokeWidth = 6f; style = Paint.Style.STROKE }
        
        // 1. Draw raw detected dots as crosses
        for (pt in rawRefPoints) {
            canvas.drawLine((pt.x - 10).toFloat(), pt.y.toFloat(), (pt.x + 10).toFloat(), pt.y.toFloat(), paintRawRef)
            canvas.drawLine(pt.x.toFloat(), (pt.y - 10).toFloat(), pt.x.toFloat(), (pt.y + 10).toFloat(), paintRawRef)
        }
        for (pt in rawLensPoints) {
            canvas.drawLine((pt.x - 10).toFloat(), pt.y.toFloat(), (pt.x + 10).toFloat(), pt.y.toFloat(), paintRawLens)
            canvas.drawLine(pt.x.toFloat(), (pt.y - 10).toFloat(), pt.x.toFloat(), (pt.y + 10).toFloat(), paintRawLens)
        }

        // 2. Draw assigned cells
        for ((rc, pt) in refGrid.points) {
            val obs = pt.observedPosition
            if (obs != null) {
                canvas.drawCircle(obs.x.toFloat(), obs.y.toFloat(), 12f, paintRefCell)
            }
        }
        for ((rc, pt) in lensGrid.points) {
            val obs = pt.observedPosition
            if (obs != null) {
                canvas.drawCircle(obs.x.toFloat(), obs.y.toFloat(), 15f, paintLensCell)
            }
        }

        // 3. Highlight common cells and draw connection vectors
        for (rc in commonKeys) {
            val rPt = refGrid.points[rc]!!.observedPosition!!
            val lPt = lensGrid.points[rc]!!.observedPosition!!
            canvas.drawCircle(rPt.x.toFloat(), rPt.y.toFloat(), 18f, paintCommonCell)
            canvas.drawLine(rPt.x.toFloat(), rPt.y.toFloat(), lPt.x.toFloat(), lPt.y.toFloat(), paintCommonCell)
        }
        
        // Add legend HUD
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
        }
        
        var y = 50f
        canvas.drawText("Grey Cross: Raw Ref (${rawRefPoints.size})", 20f, y, textPaint.apply { color = Color.LTGRAY }); y += 40f
        canvas.drawText("White Circle: Assigned Ref (${refGrid.validCellCount})", 20f, y, textPaint.apply { color = Color.WHITE }); y += 40f
        canvas.drawText("Green Cross: Raw Lens (${rawLensPoints.size})", 20f, y, textPaint.apply { color = Color.GREEN }); y += 40f
        canvas.drawText("Yellow Circle: Assigned Lens (${lensGrid.validCellCount})", 20f, y, textPaint.apply { color = Color.YELLOW }); y += 40f
        canvas.drawText("Cyan Vector: Common Cells (${commonKeys.size})", 20f, y, textPaint.apply { color = Color.CYAN }); y += 40f

        return bitmap
    }
}
