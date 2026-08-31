package com.example.analysis.v5

import org.opencv.core.Point
import kotlin.math.*

data class NeighborFeature(
    val normalizedDist: Double,
    val angle: Double // relative angle in radians [0, 2pi)
)

data class V5PointDescriptor(
    val pointIndex: Int,
    val point: Point,
    val neighbors: List<NeighborFeature>,
    val localDensity: Double
) {
    companion object {
        fun compute(pointIndex: Int, point: Point, allPoints: List<Point>, medianSpacing: Double): V5PointDescriptor {
            val k = min(8, allPoints.size - 1)
            if (k <= 0) {
                return V5PointDescriptor(pointIndex, point, emptyList(), 0.0)
            }

            val distances = mutableListOf<Pair<Point, Double>>()
            for ((idx, other) in allPoints.withIndex()) {
                if (idx == pointIndex) continue
                val dist = hypot(other.x - point.x, other.y - point.y)
                distances.add(Pair(other, dist))
            }
            distances.sortBy { it.second }

            val nearest = distances.take(k)
            val effectiveSpacing = if (medianSpacing > 0.0) medianSpacing else (nearest.firstOrNull()?.second ?: 50.0)

            val primaryAngle = if (nearest.isNotEmpty()) {
                atan2(nearest[0].first.y - point.y, nearest[0].first.x - point.x)
            } else 0.0

            val neighborFeatures = mutableListOf<NeighborFeature>()
            for ((other, dist) in nearest) {
                val normDist = dist / effectiveSpacing
                var angle = atan2(other.y - point.y, other.x - point.x) - primaryAngle
                while (angle < 0) angle += 2.0 * PI
                while (angle >= 2.0 * PI) angle -= 2.0 * PI
                neighborFeatures.add(NeighborFeature(normDist, angle))
            }

            // Local density: count points within 2.5 * effectiveSpacing
            val densityCount = distances.count { it.second <= effectiveSpacing * 2.5 }

            return V5PointDescriptor(pointIndex, point, neighborFeatures, densityCount.toDouble())
        }

        fun compare(desc1: V5PointDescriptor, desc2: V5PointDescriptor): Double {
            // Compare neighbor features and density
            val list1 = desc1.neighbors
            val list2 = desc2.neighbors
            if (list1.isEmpty() || list2.isEmpty()) return 1000.0

            var totalDiff = 0.0
            val matchCount = min(list1.size, list2.size)
            for (i in 0 until matchCount) {
                val n1 = list1[i]
                val n2 = list2[i]
                val distDiff = abs(n1.normalizedDist - n2.normalizedDist)
                var angleDiff = abs(n1.angle - n2.angle)
                if (angleDiff > PI) angleDiff = 2.0 * PI - angleDiff
                totalDiff += distDiff + angleDiff * 0.5
            }
            val densityDiff = abs(desc1.localDensity - desc2.localDensity) / max(1.0, max(desc1.localDensity, desc2.localDensity))
            return (totalDiff / matchCount) + densityDiff * 0.2
        }
    }
}
