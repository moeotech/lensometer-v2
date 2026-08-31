package com.example.analysis.v5

data class V5Telemetry(
    val referencePointCount: Int = 0,
    val lensPointCount: Int = 0,
    val referenceMedianSpacing: Double = 0.0,
    val lensMedianSpacing: Double = 0.0,
    val referenceSpacingStats: SpacingStats = SpacingStats(),
    val lensSpacingStats: SpacingStats = SpacingStats(),
    val candidatePairCount: Int = 0,
    val seedMatchCount: Int = 0,
    val predictionTx: Double = 0.0,
    val predictionTy: Double = 0.0,
    val predictionRotationDeg: Double = 0.0,
    val predictionScale: Double = 1.0,
    val predictionRms: Double = 0.0,
    val mnnCandidateCount: Int = 0,
    val mnnAcceptedCount: Int = 0,
    val mnnRejectedDistance: Int = 0,
    val mnnRejectedNonMutual: Int = 0,
    val localGeometryAccepted: Int = 0,
    val localGeometryRejected: Int = 0,
    val iterationCount: Int = 0,
    val matchesAddedPerIteration: List<Int> = emptyList(),
    val q1Matches: Int = 0,
    val q2Matches: Int = 0,
    val q3Matches: Int = 0,
    val q4Matches: Int = 0,
    val quadrantsCovered: Int = 0,
    val coveragePct: Double = 0.0,
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
