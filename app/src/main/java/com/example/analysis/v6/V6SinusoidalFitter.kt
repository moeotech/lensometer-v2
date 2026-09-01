package com.example.analysis.v6

import kotlin.math.*

object V6SinusoidalFitter {

    data class FitResult(
        val a0: Double,
        val aCos: Double,
        val aSin: Double,
        val astigmaticAmplitude: Double,
        val principalOrientation: Double,
        val fitRms: Double,
        val fitMad: Double,
        val angularCoverage: Double,
        val sampleCount: Int
    )

    fun fit(measurements: List<V6DirectionalMeasurement>): FitResult? {
        if (measurements.size < 5) return null

        val n = measurements.size
        val xMatrix = Array(n) { DoubleArray(3) }
        val yVector = DoubleArray(n)

        for (i in 0 until n) {
            val theta = Math.toRadians(measurements[i].angleDegrees)
            xMatrix[i][0] = 1.0
            xMatrix[i][1] = cos(2 * theta)
            xMatrix[i][2] = sin(2 * theta)
            yVector[i] = measurements[i].radiusRatio
        }

        var beta = solveWeightedLeastSquares(xMatrix, yVector, DoubleArray(n) { 1.0 })
        
        // IRLS with Huber weights
        val k = 1.345
        for (iter in 0..4) {
            val residuals = DoubleArray(n)
            for (i in 0 until n) {
                val pred = beta[0] + beta[1] * xMatrix[i][1] + beta[2] * xMatrix[i][2]
                residuals[i] = yVector[i] - pred
            }
            
            // Calculate MAD for robust scale estimate
            val absRes = residuals.map { abs(it) }.sorted()
            val mad = if (n > 0) absRes[n / 2] else 1.0
            val sigma = max(1e-6, mad / 0.6745)
            
            val weights = DoubleArray(n)
            for (i in 0 until n) {
                val z = abs(residuals[i]) / sigma
                weights[i] = if (z <= k) 1.0 else k / z
            }
            beta = solveWeightedLeastSquares(xMatrix, yVector, weights)
        }
        
        val a0 = beta[0]
        val aCos = beta[1]
        val aSin = beta[2]
        
        val amplitude = sqrt(aCos * aCos + aSin * aSin)
        var phase = 0.5 * atan2(aSin, aCos)
        var orientation = Math.toDegrees(phase)
        if (orientation < 0) orientation += 180.0
        
        val residuals = DoubleArray(n)
        for (i in 0 until n) {
            val pred = a0 + aCos * xMatrix[i][1] + aSin * xMatrix[i][2]
            residuals[i] = yVector[i] - pred
        }
        val rms = sqrt(residuals.map { it * it }.average())
        val mad = residuals.map { abs(it) }.sorted().let { if (it.isNotEmpty()) it[it.size / 2] else 0.0 }
        
        // Compute angular coverage
        val sortedAngles = measurements.map { it.angleDegrees % 180.0 }.sorted()
        var maxGap = 0.0
        for (i in 0 until sortedAngles.size - 1) {
            val gap = sortedAngles[i+1] - sortedAngles[i]
            if (gap > maxGap) maxGap = gap
        }
        if (sortedAngles.size > 1) {
            val wrapGap = (sortedAngles.first() + 180.0) - sortedAngles.last()
            if (wrapGap > maxGap) maxGap = wrapGap
        }
        val coverage = if (sortedAngles.isEmpty()) 0.0 else (180.0 - maxGap) / 180.0 * 100.0

        return FitResult(
            a0 = a0,
            aCos = aCos,
            aSin = aSin,
            astigmaticAmplitude = amplitude,
            principalOrientation = orientation,
            fitRms = rms,
            fitMad = mad,
            angularCoverage = coverage,
            sampleCount = n
        )
    }

    private fun solveWeightedLeastSquares(X: Array<DoubleArray>, Y: DoubleArray, W: DoubleArray): DoubleArray {
        // X is n x 3, W is length n, Y is length n
        // beta = (X^T W X)^-1 X^T W Y
        val xtwx = Array(3) { DoubleArray(3) }
        val xtwy = DoubleArray(3)
        
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0.0
                for (k in X.indices) {
                    sum += X[k][i] * W[k] * X[k][j]
                }
                xtwx[i][j] = sum
            }
            var sum = 0.0
            for (k in X.indices) {
                sum += X[k][i] * W[k] * Y[k]
            }
            xtwy[i] = sum
        }
        
        // Invert 3x3 matrix xtwx
        val inv = invert3x3(xtwx) ?: return DoubleArray(3) { 0.0 } // fallback
        
        val beta = DoubleArray(3)
        for (i in 0 until 3) {
            var sum = 0.0
            for (j in 0 until 3) {
                sum += inv[i][j] * xtwy[j]
            }
            beta[i] = sum
        }
        
        return beta
    }
    
    private fun invert3x3(m: Array<DoubleArray>): Array<DoubleArray>? {
        val det = m[0][0] * (m[1][1] * m[2][2] - m[2][1] * m[1][2]) -
                  m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
                  m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
                  
        if (abs(det) < 1e-9) return null
        
        val inv = Array(3) { DoubleArray(3) }
        inv[0][0] = (m[1][1] * m[2][2] - m[2][1] * m[1][2]) / det
        inv[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) / det
        inv[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / det
        inv[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) / det
        inv[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / det
        inv[1][2] = (m[1][0] * m[0][2] - m[0][0] * m[1][2]) / det
        inv[2][0] = (m[1][0] * m[2][1] - m[2][0] * m[1][1]) / det
        inv[2][1] = (m[2][0] * m[0][1] - m[0][0] * m[2][1]) / det
        inv[2][2] = (m[0][0] * m[1][1] - m[1][0] * m[0][1]) / det
        
        return inv
    }
}
