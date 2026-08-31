package com.example.analysis.v5

import org.opencv.core.Point

data class V5Correspondence(
    val referencePoint: Point,
    val observedPoint: Point,
    val referenceIndex: Int,
    val lensIndex: Int,
    val rawDx: Double,
    val rawDy: Double,
    val matchConfidence: Double,
    val descriptorScore: Double,
    val localConsistencyScore: Double,
    val isInlier: Boolean
)
