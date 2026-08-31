package com.example.analysis.v6

data class V6Telemetry(
    val success: Boolean = false,
    val failureReason: String = "",
    val referenceValidCells: Int = 0,
    val lensValidCells: Int = 0,
    val validDirectionalVectors: Int = 0,
    val ratioMedian: Double = 1.0,
    val ratioRange: Double = 0.0,
    val directionalConsistency: String = "FAIL",
    val gitCommit: String = "UNKNOWN",
    val deviceGeometry: V6DeviceGeometry = V6DeviceGeometry()
)
