package com.example.model

data class DisplacementVector(val rx: Double, val ry: Double, val ox: Double, val oy: Double)

data class LensMeasurementResult(
    val analysisSuccess: Boolean,
    val analysisError: String?,
    val measurementQualityPass: Boolean,
    val qualityReason: String?,
    
    val sph: Double?,
    val cyl: Double?,
    val axis: Double?,
    val calibrated: Boolean,
    
    val principal1: Double?,
    val principal2: Double?,
    val isotropic: Double?,
    val anisotropic: Double?,
    
    val principalAngle1: Double?,
    val principalAngle2: Double?,
    
    val confidence: String,
    
    val registrationRms: Double?,
    val ransacInliers: Int?,
    
    val trackedPoints: Int,
    val referencePoints: Int,
    val coverage: Int,
    
    val opticalCenterX: Double?,
    val opticalCenterY: Double?,
    
    val meanDx: Double?,
    val meanDy: Double?,

    val imageWidth: Int,
    val imageHeight: Int,
    val geometricCenterX: Double,
    val geometricCenterY: Double,
    val lensRadius: Double,
    val vectors: List<DisplacementVector>
)
