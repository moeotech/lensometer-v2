package com.example.analysis.v6

import org.opencv.core.Point

enum class V6CellRegion {
    INSIDE_LENS,
    BOUNDARY,
    OUTSIDE_LENS
}

data class V6LensRoi(
    val centerX: Double,
    val centerY: Double,
    val innerRadius: Double,
    val outerRadius: Double
) {
    fun classify(pt: Point): V6CellRegion {
        val dx = pt.x - centerX
        val dy = pt.y - centerY
        val r = Math.sqrt(dx * dx + dy * dy)
        return when {
            r < innerRadius -> V6CellRegion.INSIDE_LENS
            r > outerRadius -> V6CellRegion.OUTSIDE_LENS
            else -> V6CellRegion.BOUNDARY
        }
    }
}
