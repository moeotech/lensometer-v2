package com.example.model

data class DisplacementVector(val rx: Double, val ry: Double, val ox: Double, val oy: Double)
data class PointCoord(val x: Double, val y: Double)
data class MatchPairData(val refX: Double, val refY: Double, val lensX: Double, val lensY: Double, val accepted: Boolean, val rejectionReason: String?)

data class LensMeasurementResult(
    val analysisSuccess: Boolean,
    val analysisError: String?,
    val measurementQualityPass: Boolean,
    val qualityReason: String?,
    
    val sph: Double?,
    val cyl: Double?,
    val axis: Double?,
    val calibrated: Boolean,
    
    val principal1: Double?,
    val principal2: Double?,
    val isotropic: Double?,
    val anisotropic: Double?,
    
    val principalAngle1: Double?,
    val principalAngle2: Double?,
    
    val confidence: String,
    
    val registrationRms: Double?,
    val ransacInliers: Int?,
    
    val trackedPoints: Int,
    val referencePoints: Int,
    val coverage: Int,
    
    // Telemetry fields for correspondence pipeline inspection
    val totalReferenceDots: Int,
    val outerReferenceDotsCount: Int,
    val innerReferenceDotsCount: Int,
    val totalLensDots: Int,
    val outerLensDotsCount: Int,
    val innerLensDotsCount: Int,
    val candidateOuterMatches: Int,
    val acceptedOuterMatches: Int,
    val transformType: String,
    val innerReferenceCandidatesCount: Int,
    val innerLensCandidatesCount: Int,
    val mutualNearestNeighborMatches: Int,
    val rejectedByDistance: Int,
    val rejectedByTopology: Int,
    val rejectedByDuplicateAssignment: Int,
    val rejectedByQuadrantRoi: Int,
    val rejectedByGeometricConsistency: Int,
    val q1Matches: Int,
    val q2Matches: Int,
    val q3Matches: Int,
    val q4Matches: Int,
    val referenceInnerPoints: List<PointCoord>,
    val lensInnerPoints: List<PointCoord>,
    val acceptedMatchesList: List<MatchPairData>,
    val rejectedReferencePoints: List<PointCoord>,
    val rejectedLensPoints: List<PointCoord>,

    val opticalCenterX: Double?,
    val opticalCenterY: Double?,
    
    val meanDx: Double?,
    val meanDy: Double?,

    val imageWidth: Int,
    val imageHeight: Int,
    val geometricCenterX: Double,
    val geometricCenterY: Double,
    val lensRadius: Double,
    val vectors: List<DisplacementVector>
)
