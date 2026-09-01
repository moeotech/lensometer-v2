package com.example.analysis.v6

data class V6GridModel(
    val points: Map<Pair<Int, Int>, V6GridPoint>,
    val originX: Double,
    val originY: Double,
    val spacingX: Double,
    val spacingY: Double,
    val angleDeg: Double,
    val validCellCount: Int
)
