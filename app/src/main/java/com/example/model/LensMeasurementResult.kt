package com.example.model

data class DisplacementVector(val rx: Double, val ry: Double, val ox: Double, val oy: Double)

data class LensMeasurementResult(
    val sph: Double,
    val cyl: Double,
    val axis: Double,
    val calibrated: Boolean,
    val confidence: String,
    val trackedPoints: Int,
    val coverage: Int,
    val meanDx: Double,
    val meanDy: Double,
    val p1: Double,
    val p1Angle: Double,
    val p2: Double,
    val p2Angle: Double,
    val registrationRms: Double,
    val ransacInliers: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val geometricCenterX: Double,
    val geometricCenterY: Double,
    val opticalCenterX: Double,
    val opticalCenterY: Double,
    val lensRadius: Double,
    val vectors: List<DisplacementVector>
)
