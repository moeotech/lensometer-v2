package com.example.analysis.v6

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.opencv.core.Point
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V6SyntheticTest {

    private fun generateGrid(rows: Int = 21, cols: Int = 21, spacing: Double = 50.0): List<Point> {
        val points = mutableListOf<Point>()
        val startX = 540.0 - 10 * spacing
        val startY = 960.0 - 10 * spacing
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                points.add(Point(startX + c * spacing, startY + r * spacing))
            }
        }
        return points
    }

    @Test
    fun testZeroGridRecovery() {
        val ref = generateGrid()
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        
        assertTrue("Test failed: Should successfully analyze zero grid", result.telemetry.success)
        assertEquals("Test failed: Reference valid cells should match expected", 441, result.telemetry.referenceValidCells)
        assertEquals("Test failed: Lens valid cells should match expected", 441, result.telemetry.lensValidCells)
        
        // Zero distortion means ratio is 1.0
        assertEquals(1.0, result.telemetry.ratioMedian, 0.01)
    }
    
    @Test
    fun testIsotropicDeformation() {
        val ref = generateGrid()
        
        val center = Point(540.0, 960.0)
        // Simulate a spherical lens where r_lens = 1.05 * r_ref
        val lens = ref.map { 
            val dx = it.x - center.x
            val dy = it.y - center.y
            Point(center.x + dx * 1.05, center.y + dy * 1.05)
        }
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue(result.telemetry.success)
        assertEquals("Ratio median should be ~1.05", 1.05, result.telemetry.ratioMedian, 0.01)
        
        // Ensure ratio range is very small for isotropic
        assertTrue("Ratio range should be small", result.telemetry.ratioRange < 0.02)
    }

    @Test
    fun testMissingDots() {
        val ref = generateGrid().toMutableList()
        // Remove some points from ref
        ref.removeAt(50)
        ref.removeAt(100)
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        
        assertTrue("Test failed: Should successfully analyze with missing dots", result.telemetry.success)
        assertEquals("Test failed: Should have less than 441 valid cells", 439, result.telemetry.referenceValidCells)
    }

    @Test
    fun testAnisotropicDeformation() {
        val ref = generateGrid()
        
        val center = Point(540.0, 960.0)
        // Simulate astigmatic lens where X scales by 1.1 and Y scales by 1.0
        val lens = ref.map { 
            val dx = it.x - center.x
            val dy = it.y - center.y
            Point(center.x + dx * 1.1, center.y + dy * 1.0)
        }
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue(result.telemetry.success)
        
        // Ratio range should be around 0.1 for anisotropic
        assertTrue("Ratio range should be approx 0.1", result.telemetry.ratioRange > 0.08)
    }
}
