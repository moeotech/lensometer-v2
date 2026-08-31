package com.example.analysis.v6

import org.opencv.core.Point

data class V6GridPoint(
    val row: Int,
    val column: Int,
    val expectedPosition: Point,
    val observedPosition: Point?,
    val isValid: Boolean,
    val confidence: Double
)
