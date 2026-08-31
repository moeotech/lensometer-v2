import os

path = "app/src/test/java/com/example/analysis/V4OpticalAnalyzerTest.kt"
with open(path, "r") as f:
    text = f.read()

new_tests = """
    @Test
    fun testAG_matching_rotatedGrid() = kotlinx.coroutines.runBlocking {
        val (ref, lens) = generateSyntheticGrid(w = 640.0, h = 480.0, spacing = 30.0, rotationDeg = 15.0)
        val res = analyzer.analyzePoints(ref, lens, 640.0, 480.0, ref.size, lens.size, 30.0, mutableMapOf(), emptyList())
        assertTrue("Rotated grid should pass", res.success)
        assertEquals("Tx should be near 0", 0.0, res.registrationTx, 1.0)
        assertEquals("Ty should be near 0", 0.0, res.registrationTy, 1.0)
        assertEquals("Rot should be near 15", 15.0, res.registrationRotationDeg, 0.5)
        assertEquals("L1 should be near 0", 0.0, res.lambda1, 1e-3)
    }

    @Test
    fun testAH_matching_translatedAndRotatedGrid() = kotlinx.coroutines.runBlocking {
        val (ref, lens) = generateSyntheticGrid(w = 640.0, h = 480.0, spacing = 30.0, tx = 25.0, ty = -15.0, rotationDeg = 10.0)
        val res = analyzer.analyzePoints(ref, lens, 640.0, 480.0, ref.size, lens.size, 30.0, mutableMapOf(), emptyList())
        assertTrue("Translated+Rotated grid should pass", res.success)
        assertEquals("Tx should be near 25", 25.0, res.registrationTx, 1.0)
        assertEquals("Ty should be near -15", -15.0, res.registrationTy, 1.0)
        assertEquals("Rot should be near 10", 10.0, res.registrationRotationDeg, 0.5)
        assertEquals("L1 should be near 0", 0.0, res.lambda1, 1e-3)
    }

    @Test
    fun testAI_matching_uniformScale() = kotlinx.coroutines.runBlocking {
        val (ref, lens) = generateSyntheticGrid(w = 640.0, h = 480.0, spacing = 30.0, scale = 1.05)
        val res = analyzer.analyzePoints(ref, lens, 640.0, 480.0, ref.size, lens.size, 30.0, mutableMapOf(), emptyList())
        assertTrue("Uniform scale should pass", res.success)
        assertEquals("Scale should be near 1.05", 1.05, res.registrationScale, 0.01)
        assertEquals("L1 should be near 0", 0.0, res.lambda1, 1e-3)
    }

    @Test
    fun testAJ_matching_knownAstigmatic() = kotlinx.coroutines.runBlocking {
        val (ref, lens) = generateSyntheticGrid(w = 640.0, h = 480.0, spacing = 30.0, cylPower = 0.05, cylAxis = 45.0)
        val res = analyzer.analyzePoints(ref, lens, 640.0, 480.0, ref.size, lens.size, 30.0, mutableMapOf(), emptyList())
        assertTrue("Astigmatic grid should pass", res.success)
        assertTrue("Anisotropic should be positive", res.anisotropic > 0.01)
    }

    @Test
    fun testAK_matching_combinedMotionAndDeformation() = kotlinx.coroutines.runBlocking {
        val (ref, lens) = generateSyntheticGrid(w = 640.0, h = 480.0, spacing = 30.0, tx = 10.0, ty = 10.0, rotationDeg = 5.0, scale = 1.02, sphPower = 0.04)
        val res = analyzer.analyzePoints(ref, lens, 640.0, 480.0, ref.size, lens.size, 30.0, mutableMapOf(), emptyList())
        assertTrue("Combined motion and def should pass", res.success)
        assertEquals("Tx should be near 10", 10.0, res.registrationTx, 1.0)
        assertEquals("Rot should be near 5", 5.0, res.registrationRotationDeg, 0.5)
        assertEquals("Scale should be near 1.02", 1.02, res.registrationScale, 0.01)
        assertTrue("Isotropic should be positive", res.isotropic > 0.01)
    }
}
"""

text = text.replace("}\n", new_tests)

with open(path, "w") as f:
    f.write(text)
