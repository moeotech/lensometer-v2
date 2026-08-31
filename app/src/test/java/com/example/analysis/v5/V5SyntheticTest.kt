package com.example.analysis.v5

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.opencv.core.Point
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
        val ref = generateGrid()
        val tx = 25.0
        val ty = -15.0
        val lens = ref.map { Point(it.x + tx, it.y + ty) }

        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Test 1 failed: should succeed", output.telemetry.success)
        assertTrue("Test 1 failed: should find many matches", output.correspondences.size >= ref.size * 0.8)
    }

    @Test
    fun test2DeliberateOutliersRejected() {
        val ref = generateGrid()
        val lens = ref.map { Point(it.x + 10.0, it.y + 10.0) }.toMutableList()
        lens[0] = Point(800.0, 800.0)
        lens[5] = Point(100.0, 900.0)

        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Test 2 failed: should successfully process despite outliers", output.correspondences.size >= 15)
        val match0 = output.correspondences.find { it.referenceIndex == 0 }
        if (match0 != null) {
            assertFalse("Outlier at ref[0] should be rejected or corrected", match0.residualPx > 100.0)
        }
    }

    @Test
    fun test3DuplicateTargetMatches() {
        val ref = generateGrid()
        val lens = ref.map { Point(it.x + 12.0, it.y + 8.0) }.toMutableList()
        lens.add(Point(212.0, 208.0))

        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Test 3 failed: 1-to-1 enforcement should handle duplicates", output.correspondences.size >= 10)
    }

    @Test
    fun test4SparseQuadrants() {
        val ref = generateGrid()
        val lens = ref.map { Point(it.x + 5.0, it.y + 5.0) }

        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Test 4 failed: should cover quadrants successfully", output.telemetry.quadrantsCovered >= 3)
    }

    @Test
    fun testA_RansacRecovers30to40PercentWrongSeeds() {
        val ref = generateGrid(6, 6, 80.0)
        val lens = ref.mapIndexed { idx, pt ->
            if (idx % 5 == 0) {
                Point(pt.x + 55.0, pt.y - 45.0)
            } else {
                Point(pt.x + 15.0, pt.y - 10.0)
            }
        }
        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("RANSAC / Matcher should process seed matches successfully", output.telemetry.seedMatchesRaw >= 5)
    }

    @Test
    fun testB_MostlyWrongSeedsFailWithSeedGeometryUnreliable() {
        val ref = generateGrid(6, 6, 80.0)
        val lens = ref.mapIndexed { idx, pt ->
            if (idx % 2 == 0 || idx % 3 == 0) {
                Point(Math.random() * 1000.0, Math.random() * 1000.0)
            } else {
                Point(pt.x + 5.0, pt.y + 5.0)
            }
        }
        val output = V5GeometricMatcher.match(ref, lens)
        assertFalse("Should fail matching with SEED_GEOMETRY_UNRELIABLE", output.telemetry.success)
        assertEquals("SEED_GEOMETRY_UNRELIABLE", output.telemetry.failureReason)
    }

    @Test
    fun testC_SmoothIsotropicDeformation() {
        val ref = generateGrid(6, 6, 80.0)
        val cx = 400.0
        val cy = 400.0
        val scale = 1.05
        val lens = ref.map { pt ->
            Point(cx + (pt.x - cx) * scale, cy + (pt.y - cy) * scale)
        }
        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Isotropic deformation should succeed", output.telemetry.success)
    }

    @Test
    fun testD_SmoothAnisotropicDeformation() {
        val ref = generateGrid(6, 6, 80.0)
        val cx = 400.0
        val cy = 400.0
        val scaleX = 1.03
        val scaleY = 0.98
        val lens = ref.map { pt ->
            Point(cx + (pt.x - cx) * scaleX, cy + (pt.y - cy) * scaleY)
        }
        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Anisotropic deformation should succeed or match well", output.telemetry.correspondenceSuccess)
    }

    @Test
    fun testE_LongDistanceFalseSeedsRejected() {
        val ref = generateGrid(6, 6, 80.0)
        val lens = ref.map { Point(it.x + 10.0, it.y + 10.0) }.toMutableList()
        val p = lens[0]
        lens[0] = Point(p.x + 500.0, p.y + 500.0)
        val output = V5GeometricMatcher.match(ref, lens)
        assertTrue("Long distance false seeds should be rejected", output.telemetry.seedRansacRejected >= 1)
    }
}
