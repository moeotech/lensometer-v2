package com.example.analysis

import androidx.camera.core.ImageProxy
import kotlin.math.abs

object FocusAnalyzer {
    fun measureCenterSharpness(imageProxy: ImageProxy): Float {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride

        // Center ROI (20% of width/height)
        val roiWidth = width / 5
        val roiHeight = height / 5
        val startX = (width - roiWidth) / 2
        val startY = (height - roiHeight) / 2

        var varianceSum = 0f
        var pixelCount = 0

        val row1 = ByteArray(roiWidth)
        val row2 = ByteArray(roiWidth)

        for (y in startY until startY + roiHeight - 1) {
            buffer.position(y * rowStride + startX)
            buffer.get(row1)
            buffer.position((y + 1) * rowStride + startX)
            buffer.get(row2)

            for (x in 0 until roiWidth - 1) {
                val p00 = row1[x].toInt() and 0xFF
                val p01 = row1[x + 1].toInt() and 0xFF
                val p10 = row2[x].toInt() and 0xFF
                
                val dx = p01 - p00
                val dy = p10 - p00
                varianceSum += abs(dx) + abs(dy)
                pixelCount++
            }
        }

        return if (pixelCount > 0) varianceSum / pixelCount else 0f
    }
}
