package com.example.analysis

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class V4OpticalMathTest {

    @Test
    fun testI_repeatabilityFailsOnHighRelativeVariation() = kotlinx.coroutines.runBlocking {
        // Generate 3 runs with mean L1 around 0.10, but std dev of 0.04 (CV = 0.40)
        val run1 = V4RunResult(success = true, lambda1 = 0.06, lambda2 = 0.06, isotropic = 0.06, anisotropic = 0.0)
        val run2 = V4RunResult(success = true, lambda1 = 0.10, lambda2 = 0.10, isotropic = 0.10, anisotropic = 0.0)
        val run3 = V4RunResult(success = true, lambda1 = 0.14, lambda2 = 0.14, isotropic = 0.14, anisotropic = 0.0)
        
        // The spread is 0.08, which is < 0.15 (the old threshold).
        // But the relative variation (CV) for L1 is large: mean=0.10, std=0.0326, CV=0.326 > 0.30 (or whatever threshold)
        val result = V4OpticalAnalyzer.calculateRepeatability(listOf(run1, run2, run3))
        
        assertFalse("Repeatability should fail due to high CV", result.success)
        assertTrue(result.errorMessage.contains("MEASUREMENT UNSTABLE"))
    }
}
