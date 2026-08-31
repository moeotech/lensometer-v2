package com.example.analysis.v6

data class V6GridModel(
    val points: Map<Pair<Int, Int>, V6GridPoint>,
    val centerRow: Int,
    val centerColumn: Int,
    val orientationAngle: Double,
    val validCellCount: Int
)
