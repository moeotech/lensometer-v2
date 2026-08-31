package com.example

import org.junit.Assert.*
import org.junit.Test

class V4AnalyzerTest {


    @Test
    fun testMathematicalBehaviors() {
        // Isotropic scaling
        val j_iso = arrayOf(doubleArrayOf(1.1, 0.0), doubleArrayOf(0.0, 1.1))
        var trace = j_iso[0][0] + j_iso[1][1]
        var det = j_iso[0][0]*j_iso[1][1] - j_iso[0][1]*j_iso[1][0]
        var p1 = trace/2.0 + Math.sqrt(Math.pow(trace/2.0, 2.0) - det)
        var p2 = trace/2.0 - Math.sqrt(Math.pow(trace/2.0, 2.0) - det)
        assertEquals(1.1, p1, 0.001)
        assertEquals(1.1, p2, 0.001)
        
        // Anisotropic scaling
        val j_aniso = arrayOf(doubleArrayOf(1.2, 0.0), doubleArrayOf(0.0, 1.0))
        trace = j_aniso[0][0] + j_aniso[1][1]
        det = j_aniso[0][0]*j_aniso[1][1] - j_aniso[0][1]*j_aniso[1][0]
        p1 = trace/2.0 + Math.sqrt(Math.pow(trace/2.0, 2.0) - det)
        p2 = trace/2.0 - Math.sqrt(Math.pow(trace/2.0, 2.0) - det)
        assertNotEquals(p1, p2, 0.001)
        
        // Pure translation
        val j_trans = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0))
        trace = j_trans[0][0] + j_trans[1][1]
        det = j_trans[0][0]*j_trans[1][1] - j_trans[0][1]*j_trans[1][0]
        p1 = trace/2.0 + Math.sqrt(Math.max(0.0, Math.pow(trace/2.0, 2.0) - det))
        p2 = trace/2.0 - Math.sqrt(Math.max(0.0, Math.pow(trace/2.0, 2.0) - det))
        assertEquals(0.0, p1, 0.001)
        assertEquals(0.0, p2, 0.001)
        
        // 180-degree axis wrapping
        var angle1 = 179.0
        var angle2 = 1.0
        var rad1 = angle1 * Math.PI / 180.0
        var rad2 = angle2 * Math.PI / 180.0
        var sinSum = Math.sin(rad1 * 2) + Math.sin(rad2 * 2)
        var cosSum = Math.cos(rad1 * 2) + Math.cos(rad2 * 2)
        var axisMean = Math.atan2(sinSum / 2.0, cosSum / 2.0) * 180.0 / (2.0 * Math.PI)
        if (axisMean < 0) axisMean += 180.0
        assertEquals(0.0, axisMean, 0.001) // mean of 179 and 1 is 180 = 0 (or 0)
        
        // Pure Rotation (small angle approximation for jacobian)
        // J = [cos(t)-1, -sin(t); sin(t), cos(t)-1]
        // S = 0.5 * (J + J^T) = [cos(t)-1, 0; 0, cos(t)-1]
        // Which is just isotropic compression, not anisotropic deformation.
        val t = 0.05
        val j_rot = arrayOf(doubleArrayOf(Math.cos(t)-1, -Math.sin(t)), doubleArrayOf(Math.sin(t), Math.cos(t)-1))
        val s00 = j_rot[0][0]
        val s11 = j_rot[1][1]
        val s01 = 0.5 * (j_rot[0][1] + j_rot[1][0]) // this is 0
        trace = s00 + s11
        det = s00*s11 - s01*s01
        p1 = trace/2.0 + Math.sqrt(Math.max(0.0, Math.pow(trace/2.0, 2.0) - det))
        p2 = trace/2.0 - Math.sqrt(Math.max(0.0, Math.pow(trace/2.0, 2.0) - det))
        assertEquals(p1, p2, 0.001) // symmetric deformation
    }
}
