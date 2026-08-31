package com.example.analysis

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LensAnalyzerTest {

    private fun createGrid(w: Int, h: Int, spacing: Double): List<LensAnalyzer.LocalPoint> {
        val pts = mutableListOf<LensAnalyzer.LocalPoint>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                pts.add(LensAnalyzer.LocalPoint(x * spacing, y * spacing))
            }
        }
        return pts
    }

    private fun applyTransform(pts: List<LensAnalyzer.LocalPoint>, transform: (Double, Double) -> Pair<Double, Double>): List<LensAnalyzer.LocalPoint> {
        return pts.map {
            val (nx, ny) = transform(it.x, it.y)
            LensAnalyzer.LocalPoint(nx, ny)
        }
    }

    private fun computeP(ptsX: List<LensAnalyzer.LocalPoint>, disps: List<LensAnalyzer.LocalPoint>): DoubleArray {
        val affine = LensAnalyzer.computeAffine(ptsX, disps)!!
        val A = affine[0]; val B = affine[1]; val D = affine[3]; val E = affine[4]
        val Sxy = (B + D) / 2.0
        val tr = A + E
        val detS = A * E - Sxy * Sxy
        val root = sqrt(max(0.0, tr * tr / 4.0 - detS))
        val L1 = tr / 2.0 + root
        val L2 = tr / 2.0 - root
        var theta1 = atan2(L1 - A, Sxy) * 180.0 / PI
        if (theta1 < 0) theta1 += 180.0
        return doubleArrayOf(L1, L2, theta1)
    }

    @Test
    fun pureTranslation_deformationZero() {
        val src = createGrid(5, 5, 10.0)
        val dst = applyTransform(src) { x, y -> Pair(x + 5.0, y - 3.0) }
        val disps = src.zip(dst).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        
        val p = computeP(src, disps)
        assertEquals(0.0, p[0], 1e-5)
        assertEquals(0.0, p[1], 1e-5)
    }

    @Test
    fun pureRotation_symmetricDeformationZero() {
        val src = createGrid(5, 5, 10.0)
        val angle = 5.0 * Math.PI / 180.0
        val dst = applyTransform(src) { x, y -> 
            Pair(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle)) 
        }
        val disps = src.zip(dst).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        
        val p = computeP(src, disps)
        assertEquals(cos(angle) - 1.0, p[0], 1e-5)
        assertEquals(cos(angle) - 1.0, p[1], 1e-5)
    }

    @Test
    fun isotropicScaling_P1approxP2() {
        val src = createGrid(5, 5, 10.0)
        val scale = 1.1
        val dst = applyTransform(src) { x, y -> Pair(x * scale, y * scale) }
        val disps = src.zip(dst).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        
        val p = computeP(src, disps)
        assertEquals(0.1, p[0], 1e-5)
        assertEquals(0.1, p[1], 1e-5)
    }

    @Test
    fun horizontalStretch_anisotropicWithCorrectOrientation() {
        val src = createGrid(5, 5, 10.0)
        val stretchX = 1.2 
        val stretchY = 1.0
        val dst = applyTransform(src) { x, y -> Pair(x * stretchX, y * stretchY) }
        val disps = src.zip(dst).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        
        val p = computeP(src, disps)
        assertEquals(0.2, p[0], 1e-5)
        assertEquals(0.0, p[1], 1e-5)
        assertTrue(abs(p[2]) < 1e-5 || abs(p[2] - 180.0) < 1e-5)
    }

    @Test
    fun rotatedStretch_orientationFollows() {
        val src = createGrid(5, 5, 10.0)
        val stretch = 1.2
        val angle = 45.0 * Math.PI / 180.0
        val dst = applyTransform(src) { x, y ->
            val rx = x * cos(-angle) - y * sin(-angle)
            val ry = x * sin(-angle) + y * cos(-angle)
            val sx = rx * stretch
            val sy = ry
            val fx = sx * cos(angle) - sy * sin(angle)
            val fy = sx * sin(angle) + sy * cos(angle)
            Pair(fx, fy)
        }
        val disps = src.zip(dst).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        
        val p = computeP(src, disps)
        assertEquals(0.2, p[0], 1e-5)
        assertEquals(0.0, p[1], 1e-5)
        assertTrue(abs(p[2] - 45.0) < 1e-5 || abs(p[2] - 225.0) < 1e-5)
    }

    @Test
    fun axisWrapping_180degrees() {
        val src = createGrid(5, 5, 10.0)
        val stretch = 1.2
        
        val angle1 = 175.0 * Math.PI / 180.0
        val dst1 = applyTransform(src) { x, y ->
            val rx = x * cos(-angle1) - y * sin(-angle1)
            val ry = x * sin(-angle1) + y * cos(-angle1)
            val sx = rx * stretch
            val sy = ry
            val fx = sx * cos(angle1) - sy * sin(angle1)
            val fy = sx * sin(angle1) + sy * cos(angle1)
            Pair(fx, fy)
        }
        val disps1 = src.zip(dst1).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        val p1 = computeP(src, disps1)
        
        val angle2 = -5.0 * Math.PI / 180.0
        val dst2 = applyTransform(src) { x, y ->
            val rx = x * cos(-angle2) - y * sin(-angle2)
            val ry = x * sin(-angle2) + y * cos(-angle2)
            val sx = rx * stretch
            val sy = ry
            val fx = sx * cos(angle2) - sy * sin(angle2)
            val fy = sx * sin(angle2) + sy * cos(angle2)
            Pair(fx, fy)
        }
        val disps2 = src.zip(dst2).map { p -> LensAnalyzer.LocalPoint(p.second.x - p.first.x, p.second.y - p.first.y) }
        val p2 = computeP(src, disps2)
        
        assertEquals(175.0, p1[2], 1e-4)
        assertEquals(175.0, p2[2], 1e-4)
    }
}
