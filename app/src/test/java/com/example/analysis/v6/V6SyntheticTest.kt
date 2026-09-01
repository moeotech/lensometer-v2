package com.example.analysis.v6

import org.junit.Assert.*
import org.junit.Before
import org.opencv.android.OpenCVLoader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.opencv.core.Point
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V6SyntheticTest {

    @Before
    fun setUp() {
        OpenCVLoader.initDebug()
    }

    private fun generateGrid(rows: Int = 21, cols: Int = 21, spacingX: Double = 50.0, spacingY: Double = 50.0, angleDeg: Double = 0.0, dx: Double = 0.0, dy: Double = 0.0): List<Point> {
        val points = mutableListOf<Point>()
        val startX = 540.0 - 10 * spacingX
        val startY = 960.0 - 10 * spacingY
        val rad = angleDeg * PI / 180.0
            
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = startX + c * spacingX - 540.0
                val y = startY + r * spacingY - 960.0
                val rx = x * cos(rad) - y * sin(rad)
                val ry = x * sin(rad) + y * cos(rad)
                points.add(Point(rx + 540.0 + dx, ry + 960.0 + dy))
            }
        }
        return points
    }

    @Test
    fun testOffsetRoiCenter() {
        val ref = generateGrid()
            
        // Let's create an offset ROI
        val roiCenter = Point(200.0, 300.0)
        val roi = V6LensRoi(
            centerX = roiCenter.x,
            centerY = roiCenter.y,
            innerRadius = 200.0,
            outerRadius = 400.0
        )
            
        // Deform ONLY inside the ROI
        val lens = ref.map { pt ->
            val dx = pt.x - roiCenter.x
            val dy = pt.y - roiCenter.y
            val r = sqrt(dx * dx + dy * dy)
            if (r < 200.0) {
                // Inside ROI: Apply deformation
                Point(roiCenter.x + dx * 1.1, roiCenter.y + dy * 1.1)
            } else {
                // Outside ROI: Keep original
                Point(pt.x, pt.y)
            }
        }
            
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens, 1080, 1920, roi, "TEST")
            
        assertTrue("Test failed: Should successfully analyze with offset ROI", result.telemetry.success)
        assertEquals("Lens ROI Source should be TEST", "TEST", result.telemetry.lensRoiSource)
        assertEquals("Lens ROI X", 200.0, result.telemetry.lensRoiCenterX, 0.01)
        assertEquals("Lens ROI Y", 300.0, result.telemetry.lensRoiCenterY, 0.01)
            
        // Check if deformation was measured correctly
        assertEquals("Median ratio should be approx 1.1", 1.1, result.telemetry.acceptedRatioMedian, 0.02)
            
        // Check that outside registration cells exist
        assertTrue("Should have some outside registration cells", result.telemetry.outsideLensRegistrationCells > 10)
        assertTrue("Should have some inside cells", result.telemetry.insideLensCommonCells > 10)
    }


    @Test
    fun testRoiValidationNegativeCenter() {
        val (rejected, reason) = V6StructuredDeflectometryAnalyzer.validateRoi(-10.0, 500.0, 100.0, 100.0, 1080, 1920)
        assertTrue(rejected)
        assertEquals("Center outside image", reason)
    }

    @Test
    fun testRoiValidationCenterOutsideFrame() {
        val (rejected, reason) = V6StructuredDeflectometryAnalyzer.validateRoi(1200.0, 500.0, 100.0, 100.0, 1080, 1920)
        assertTrue(rejected)
        assertEquals("Center outside image", reason)
    }

    @Test
    fun testRoiValidationEllipseTooLarge() {
        val (rejected, reason) = V6StructuredDeflectometryAnalyzer.validateRoi(540.0, 960.0, 1500.0, 1500.0, 1080, 1920)
        assertTrue(rejected)
        assertEquals("Radius too large", reason)
    }

    @Test
    fun testRoiValidationExtremeAspectRatio() {
        val (rejected, reason) = V6StructuredDeflectometryAnalyzer.validateRoi(540.0, 960.0, 300.0, 800.0, 1080, 1920)
        assertTrue(rejected)
        assertEquals("Extreme aspect ratio", reason)
    }

    @Test
    fun testZeroGridRecovery() {
        val ref = generateGrid()
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
            
        assertTrue("Test failed: Should successfully analyze zero grid", result.telemetry.success)
        assertEquals("Test failed: Reference valid cells should match expected", 441, result.telemetry.referenceAssignedCells)
        assertEquals("Test failed: Lens valid cells should match expected", 441, result.telemetry.lensAssignedCells)
        assertEquals(1.0, result.telemetry.ratioMedian, 0.01)
    }
    
    @Test
    fun testIsotropicDeformation() {
        val ref = generateGrid()
        val center = Point(540.0, 960.0)
        val lens = ref.map { 
            val dx = it.x - center.x
            val dy = it.y - center.y
            Point(center.x + dx * 1.05, center.y + dy * 1.05)
        }
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue(result.telemetry.success)
        assertEquals("Ratio median should be ~1.05", 1.05, result.telemetry.ratioMedian, 0.01)
        assertTrue("Ratio range should be small", result.telemetry.ratioRange < 0.02)
    }

    @Test
    fun testMissingDots() {
        val ref = generateGrid().toMutableList()
        ref.removeAt(50)
        ref.removeAt(100)
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        
        assertTrue("Test failed: Should successfully analyze with missing dots", result.telemetry.success)
        assertEquals("Test failed: Should have less than 441 valid cells", 439, result.telemetry.referenceAssignedCells)
        assertEquals(439, result.telemetry.commonGridCells)
    }

    @Test
    fun testAnisotropicDeformation() {
        val ref = generateGrid()
        val center = Point(540.0, 960.0)
        val lens = ref.map { 
            val dx = it.x - center.x
            val dy = it.y - center.y
            Point(center.x + dx * 1.1, center.y + dy * 1.0)
        }
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue(result.telemetry.success)
        assertTrue("Ratio range should be approx 0.1", result.telemetry.ratioRange > 0.08)
    }

    @Test
    fun testRotatedGrid0() {
        val ref = generateGrid(angleDeg = 0.0)
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        assertTrue(result.telemetry.success)
        assertEquals(441, result.telemetry.commonGridCells)
    }

    @Test
    fun testRotatedGrid5() {
        val ref = generateGrid(angleDeg = 5.0)
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        assertTrue(result.telemetry.success)
        assertEquals(441, result.telemetry.commonGridCells)
        assertTrue(abs(result.telemetry.estimatedGridAngleDeg - 5.0) < 1.0 || abs(result.telemetry.estimatedGridAngleDeg - 85.0) < 1.0)
    }

    @Test
    fun testRotatedGridMinus10() {
        val ref = generateGrid(angleDeg = -10.0)
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, ref)
        assertTrue(result.telemetry.success)
        assertEquals(441, result.telemetry.commonGridCells)
    }

    @Test
    fun testTranslatedGrid() {
        val ref = generateGrid()
        // Lens is isotropic + shifted
        val center = Point(540.0, 960.0)
        val lens = ref.map { 
            val dx = it.x - center.x
            val dy = it.y - center.y
            Point(center.x + dx * 1.05 + 10.5, center.y + dy * 1.05 - 8.2)
        }
        
        val result = V6StructuredDeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue(result.telemetry.success)
        assertEquals(441, result.telemetry.commonGridCells)
        assertEquals(1.05, result.telemetry.ratioMedian, 0.01)
    }
}
