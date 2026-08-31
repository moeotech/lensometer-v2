package com.example.analysis.v5

import org.opencv.core.Point
import kotlin.math.*

data class V5RawFieldResult(
    val meanDx: Double,
    val meanDy: Double,
    val jacobian: DoubleArray,
    val symJ: DoubleArray,
    val principalValue1: Double,
    val principalValue2: Double,
    val principalAngleDeg: Double,
    val isotropicComponent: Double,
    val anisotropicComponent: Double
)

object V5FieldAnalyzer {
    fun analyze(correspondences: List<V5Correspondence>): V5RawFieldResult? {
        val inliers = correspondences.filter { it.isInlier }
        if (inliers.size < 10) return null

        val meanDx = inliers.map { it.rawDx }.average()
        val meanDy = inliers.map { it.rawDy }.average()

        val cx = inliers.map { it.referencePoint.x }.average()
        val cy = inliers.map { it.referencePoint.y }.average()

        var sumX2 = 0.0
        var sumY2 = 0.0
        var sumXY = 0.0
        var sumXU = 0.0
        var sumYU = 0.0
        var sumXV = 0.0
        var sumYV = 0.0

        for (c in inliers) {
            val x = c.referencePoint.x - cx
            val y = c.referencePoint.y - cy
            val u = c.rawDx
            val v = c.rawDy

            sumX2 += x * x
            sumY2 += y * y
            sumXY += x * y
            sumXU += x * u
            sumYU += y * u
            sumXV += x * v
            sumYV += y * v
        }

        val det = sumX2 * sumY2 - sumXY * sumXY
        val (a1, a2, b1, b2) = if (abs(det) > 1e-5) {
            val a1 = (sumXU * sumY2 - sumYU * sumXY) / det
            val a2 = (sumYU * sumX2 - sumXU * sumXY) / det
            val b1 = (sumXV * sumY2 - sumYV * sumXY) / det
            val b2 = (sumYV * sumX2 - sumXV * sumXY) / det
            listOf(a1, a2, b1, b2)
        } else {
            listOf(0.0, 0.0, 0.0, 0.0)
        }

        val j00 = 1.0 + a1
        val j01 = a2
        val j10 = b1
        val j11 = 1.0 + b2

        val s00 = j00
        val s01 = 0.5 * (j01 + j10)
        val s10 = s01
        val s11 = j11

        val trace = s00 + s11
        val disc = sqrt(max(0.0, (s00 - s11)*(s00 - s11) + 4.0 * s01 * s01))
        val lam1 = 0.5 * (trace + disc)
        val lam2 = 0.5 * (trace - disc)

        val angleRad = 0.5 * atan2(2.0 * s01, s00 - s11)
        val angleDeg = Math.toDegrees(angleRad)

        val iso = 0.5 * (lam1 + lam2)
        val aniso = 0.5 * abs(lam1 - lam2)

        return V5RawFieldResult(
            meanDx = meanDx,
            meanDy = meanDy,
            jacobian = doubleArrayOf(j00, j01, j10, j11),
            symJ = doubleArrayOf(s00, s01, s10, s11),
            principalValue1 = lam1,
            principalValue2 = lam2,
            principalAngleDeg = angleDeg,
            isotropicComponent = iso,
            anisotropicComponent = aniso
        )
    }
}
