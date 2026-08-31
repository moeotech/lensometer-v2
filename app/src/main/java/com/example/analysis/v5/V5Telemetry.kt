package com.example.analysis.v5

data class V5Telemetry(
    val referencePointCount: Int = 0,
    val lensPointCount: Int = 0,
    val referenceMedianSpacing: Double = 0.0,
    val lensMedianSpacing: Double = 0.0,
    val referenceSpacingStats: SpacingStats = SpacingStats(),
    val lensSpacingStats: SpacingStats = SpacingStats(),
    val initialCandidateMatches: Int = 0,
    val seedMatchCount: Int = 0,
    val preValidationMatches: Int = 0,
    val acceptedInlierMatches: Int = 0,
    val rejectedResidual: Int = 0,
    val rejectedLocalConsistency: Int = 0,
    val rejectedCollision: Int = 0,
    val rejectedTransform: Int = 0,
    val medianResidualPx: Double = 0.0,
    val residualMadPx: Double = 0.0,
    val maxAcceptedResidualPx: Double = 0.0,
    val transformInliers: Int = 0,
    val transformRms: Double = 0.0,
    val predictionTx: Double = 0.0,
    val predictionTy: Double = 0.0,
    val predictionRotationDeg: Double = 0.0,
    val predictionScale: Double = 1.0,
    val iterationCount: Int = 0,
    val matchesAddedPerIteration: List<Int> = emptyList(),
    val q1Matches: Int = 0,
    val q2Matches: Int = 0,
    val q3Matches: Int = 0,
    val q4Matches: Int = 0,
    val quadrantsCovered: Int = 0,
    val coveragePct: Double = 0.0,
    val correspondenceSuccess: Boolean = false,
    val measurementQualityValid: Boolean = false,
    val success: Boolean = false,
    val failureReason: String = ""
)

data class SpacingStats(
    val min: Double = 0.0,
    val p25: Double = 0.0,
    val median: Double = 0.0,
    val p75: Double = 0.0,
    val max: Double = 0.0,
    val mean: Double = 0.0
)
