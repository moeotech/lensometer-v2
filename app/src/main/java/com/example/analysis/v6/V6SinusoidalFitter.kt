package com.example.analysis.v6

import kotlin.math.*

object V6SinusoidalFitter {
    
    data class FitResult(
        val a0: Double,
        val aCos: Double,
        val aSin: Double,
        val meanPower: Double,
        val astigmaticAmplitude: Double,
        val principalOrientation: Double
    )

    fun fit(measurements: List<V6DirectionalMeasurement>): FitResult? {
        if (measurements.isEmpty()) return null
        
        var sumY = 0.0
        
        for (m in measurements) {
            val y = m.radiusRatio
            sumY += y
        }
        
        val a0 = sumY / measurements.size
        
        var numAcos = 0.0
        var denAcos = 0.0
        var numAsin = 0.0
        var denAsin = 0.0
        
        for (m in measurements) {
            val theta = Math.toRadians(m.angleDegrees)
            val y = m.radiusRatio
            val x1 = cos(2 * theta)
            val x2 = sin(2 * theta)
            
            numAcos += x1 * (y - a0)
            denAcos += x1 * x1
            
            numAsin += x2 * (y - a0)
            denAsin += x2 * x2
        }
        
        val aCos = if (denAcos > 0) numAcos / denAcos else 0.0
        val aSin = if (denAsin > 0) numAsin / denAsin else 0.0
        
        val amplitude = sqrt(aCos * aCos + aSin * aSin)
        val phase = 0.5 * atan2(aSin, aCos)
        var orientation = Math.toDegrees(phase)
        if (orientation < 0) orientation += 180.0
        
        return FitResult(
            a0 = a0,
            aCos = aCos,
            aSin = aSin,
            meanPower = a0, // Experimental
            astigmaticAmplitude = amplitude,
            principalOrientation = orientation
        )
    }
}
