package com.example.analysis.v6

data class V6DeviceGeometry(
    val cameraToLensDistanceMm: Double = 150.0,
    val lensToTargetDistanceMm: Double = 100.0,
    val targetDotSpacingMm: Double = 2.0,
    val targetVersion: String = "V6-Alpha-1",
    val phoneModel: String = "Unknown",
    val cameraResolution: String = "1920x1080",
    val lensOrientation: String = "Unknown"
)
