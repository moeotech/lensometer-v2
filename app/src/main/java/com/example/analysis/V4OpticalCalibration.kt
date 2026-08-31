package com.example.analysis

import kotlin.math.*

data class ExperimentalPowerEstimate(
    val principal1: Double?,
    val principal2: Double?,
    val sphere: Double?,
    val cylinder: Double?,
    val axis: Double?,
    val confidence: Double,
    val calibrationStatus: String
)

data class CalibrationSample(
    val e1: Double,
    val e2: Double,
    val isotropic: Double,
    val anisotropic: Double,
    val knownSphere: Double,
    val knownCylinder: Double,
    val knownAxis: Double?,
    val fitRms: Double,
    val coverage: Double,
    val inlierCount: Int,
    val qualityPass: Boolean
)

object V4OpticalCalibration {

    fun estimatePower(
        lambda1: Double,
        lambda2: Double,
        axisRad: Double,
        qualityPass: Boolean,
        fitRms: Double
    ): ExperimentalPowerEstimate {
        val e1 = lambda1
        val e2 = lambda2
        val p1 = max(e1, e2)
        val p2 = min(e1, e2)
        
        val sphere = p1
        val cylinder = p2 - p1
        
        var finalAxis = axisRad * 180 / PI
        if (finalAxis < 0) finalAxis += 180.0
        if (finalAxis >= 180) finalAxis -= 180.0
        
        val uncalibratedScale = 100.0 
        
        val isUndefinedAxis = abs(cylinder) < 0.005
        val axisOutput = if (isUndefinedAxis) null else finalAxis
        
        val confidence = if (qualityPass && fitRms < 5.0) 0.8 else 0.3
        
        return ExperimentalPowerEstimate(
            principal1 = p1 * uncalibratedScale,
            principal2 = p2 * uncalibratedScale,
            sphere = sphere * uncalibratedScale,
            cylinder = cylinder * uncalibratedScale,
            axis = axisOutput,
            confidence = confidence,
            calibrationStatus = "UNCALIBRATED"
        )
    }
}
