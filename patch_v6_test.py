import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

new_test = """
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
"""

content = content.replace('}', new_test + '\n}', 1)

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
