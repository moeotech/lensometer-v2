package com.example.analysis.v6

data class V6Telemetry(
    val success: Boolean = false,
    val failureReason: String = "",
    
    val detectedReferencePoints: Int = 0,
    val detectedLensPoints: Int = 0,
    
    val estimatedGridAngleDeg: Double = 0.0,
    val estimatedSpacingX: Double = 0.0,
    val estimatedSpacingY: Double = 0.0,
    val gridOriginX: Double = 0.0,
    val gridOriginY: Double = 0.0,
    
    val referenceAssignedCells: Int = 0,
    val lensAssignedCells: Int = 0,
    val commonGridCells: Int = 0,
    val referenceAssignmentPct: Double = 0.0,
    val lensAssignmentPct: Double = 0.0,
    
    val insideLensRefCells: Int = 0,
    val insideLensLensCells: Int = 0,
    val insideLensCommonCells: Int = 0,
    val outsideLensRegistrationCells: Int = 0,
    
    val rawVectorCount: Int = 0,
    val acceptedVectorCount: Int = 0,
    
    val rejectedRatioOutlier: Int = 0,
    val rejectedGridMismatch: Int = 0,
    val rejectedBoundary: Int = 0,
    val rejectedSpatialConsistency: Int = 0,
    
    val globalTx: Double = 0.0,
    val globalTy: Double = 0.0,
    val globalRotation: Double = 0.0,
    val globalScale: Double = 0.0,
    
    val rawRatioMedian: Double = 0.0,
    val acceptedRatioMedian: Double = 0.0,
    val acceptedRatioMAD: Double = 0.0,
    val acceptedRatioP05: Double = 0.0,
    val acceptedRatioP95: Double = 0.0,
    
    val validDirectionalVectors: Int = 0,
    val ratioMedian: Double = 1.0,
    val ratioRange: Double = 0.0,
    val directionalConsistency: String = "FAIL",
    val directionalConsistencyReason: String = "",
    
    val gitCommit: String = "UNKNOWN",
    val deviceGeometry: V6DeviceGeometry = V6DeviceGeometry()
)
