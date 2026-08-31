package com.example.analysis.v5

import android.graphics.Bitmap
import org.opencv.core.Point

data class V5MatchResult(
    val success: Boolean,
    val errorMessage: String = "",
    val correspondences: List<V5Correspondence> = emptyList(),
    val telemetry: V5Telemetry = V5Telemetry(),
    val debugBitmap: Bitmap? = null,
    val rawFieldResult: V5RawFieldResult? = null,
    val referencePoints: List<Point> = emptyList(),
    val lensPoints: List<Point> = emptyList()
)
