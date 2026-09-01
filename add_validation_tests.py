import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

new_tests = """
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
        val (rejected, reason) = V6StructuredDeflectometryAnalyzer.validateRoi(540.0, 960.0, 100.0, 400.0, 1080, 1920)
        assertTrue(rejected)
        assertEquals("Extreme aspect ratio", reason)
    }
"""

content = content.replace("    @Test\n    fun testZeroGridRecovery() {", new_tests + "\n    @Test\n    fun testZeroGridRecovery() {")

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
print("Tests added successfully.")
