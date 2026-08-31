package com.example.analysis.v6

data class V6TargetDefinition(
    val rowCount: Int = 21,
    val columnCount: Int = 21,
    val centerRow: Int = 10,
    val centerColumn: Int = 10,
    val expectedPhysicalSpacingMm: Double = 2.0
)
