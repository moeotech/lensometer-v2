package com.example.analysis.v5

import org.junit.Assert.*
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.Point
import kotlin.math.*

class V5SyntheticTest {

    private fun generateGrid(rows: Int = 6, cols: Int = 6, spacing: Double = 80.0): List<Point> {
        val points = mutableListOf<Point>()
        val startX = 200.0
        val startY = 200.0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                points.add(Point(startX + c * spacing, startY + r * spacing))
            }
        }
        return points
    }

    @Test
    fun test1PureTranslation() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val ref = generateGrid()
        val tx = 25.0
        val ty = -15.0
        val lens = ref.map { Point(it.x + tx, it.y + ty) }

        val result = V5DeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue("Test 1 failed: should succeed", result.success)
        assertTrue("Test 1 failed: should find many matches", result.correspondences.size >= ref.size * 0.8)
    }

    @Test
    fun test4IsotropicDeformation() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val ref = generateGrid()
        val cx = 400.0
        val cy = 400.0
        val scale = 1.15
        val lens = ref.map { Point(cx + (it.x - cx) * scale, cy + (it.y - cy) * scale) }

        val result = V5DeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue("Test 4 failed: should succeed", result.success)
        assertNotNull("Raw field result should not be null", result.rawFieldResult)
        result.rawFieldResult?.let {
            assertTrue("Isotropic component should reflect scale expansion", it.isotropicComponent > 1.0)
        }
    }

    @Test
    fun test7MissingPoints() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val ref = generateGrid()
        // Remove 10% of points from lens
        val lens = ref.mapIndexed { idx, pt -> if (idx % 10 == 0) null else Point(pt.x + 10.0, pt.y + 5.0) }.filterNotNull()

        val result = V5DeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue("Test 7 failed: should handle missing points", result.success)
    }

    @Test
    fun test9FalseExtraPoints() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val ref = generateGrid()
        val lens = ref.map { Point(it.x + 5.0, it.y + 5.0) }.toMutableList()
        lens.add(Point(250.0, 250.0))
        lens.add(Point(450.0, 350.0))
        lens.add(Point(650.0, 550.0))

        val result = V5DeflectometryAnalyzer.analyzePoints(ref, lens)
        assertTrue("Test 9 failed: should handle extra false points", result.success)
    }
}
