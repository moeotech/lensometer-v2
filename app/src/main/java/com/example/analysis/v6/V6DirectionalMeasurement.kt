package com.example.analysis.v6

import org.opencv.core.Point

data class V6DirectionalMeasurement(
    val angleDegrees: Double,
    val referenceRadius: Double,
    val lensRadius: Double,
    val radiusRatio: Double,
    val gridStepDelta: Pair<Int, Int> = Pair(0, 0),
    val position: Point = Point(0.0, 0.0)
)
