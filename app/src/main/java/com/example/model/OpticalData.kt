package com.example.model

data class FrameMeasurement(
    val frameIndex: Int,
    val timestamp: Long,
    val isFlashOn: Boolean,
    val isWithLens: Boolean,
    val avgLuminance: Float,
    val maxLuminance: Int,
    val stdDevLuminance: Float,
    val brightPixelCount: Int,
    val brightPixelPercentage: Float,
    val centerLuminance: Float,
    val leftLuminance: Float,
    val rightLuminance: Float,
    val topLuminance: Float,
    val bottomLuminance: Float,
    val sharpness: Float,
    val flashSignal: Float,
    val reflectionCandidates: List<ReflectionCandidate>
)

data class ReflectionCandidate(
    val id: Int,
    val centroidX: Float,
    val centroidY: Float,
    val area: Int,
    val peakBrightness: Int,
    val avgBrightness: Float,
    val boundingBox: BoundingBox
)

data class BoundingBox(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
)

data class PhaseStats(
    val phaseName: String,
    val frames: List<FrameMeasurement>,
    val meanLuminance: Float,
    val stdDevLuminance: Float,
    val meanSharpness: Float,
    val stdDevSharpness: Float,
    val meanBrightPixels: Float,
    val stdDevBrightPixels: Float,
    val meanFlashSignal: Float,
    val stdDevFlashSignal: Float,
    val meanReflections: Float,
    val stdDevReflections: Float,
    // Coefficient of variation (CV) = stdDev / mean
    val cvLuminance: Float,
    val cvSharpness: Float,
    val cvBrightPixels: Float,
    val cvFlashSignal: Float,
    val cvReflections: Float
)

data class ExperimentReport(
    val timestamp: Long,
    val noLens1: PhaseStats,
    val noLens2: PhaseStats,
    val lens1: PhaseStats,
    val lens2: PhaseStats,
    val noLensRepeatability: Float, // Difference between NoLens1 and NoLens2 (e.g., avg brightness/sharpness diff)
    val lensRepeatability: Float, // Difference between Lens1 and Lens2
    val betweenStateDifference: Float, // Difference between NoLens(avg) and Lens(avg)
    val opticalDifferenceDetected: Boolean,
    val observationText: String
)
