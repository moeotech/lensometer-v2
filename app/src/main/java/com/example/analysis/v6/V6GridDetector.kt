package com.example.analysis.v6

import org.opencv.core.Point
import kotlin.math.*

object V6GridDetector {
    private fun deltaAngle(a1: Double, a2: Double): Double {
        val diff = abs(a1 - a2) % 90.0
        return min(diff, 90.0 - diff)
    }

    fun recoverZeroGrid(points: List<Point>): V6GridModel {
        if (points.size < 3) {
            return V6GridModel(emptyMap(), 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }

        // 1. Min dists to find median spacing
        val minDists = points.map { p1 ->
            points.filter { it != p1 }.minOf { p2 -> hypot(p1.x - p2.x, p1.y - p2.y) }
        }.sorted()
        val medianMinDist = minDists[minDists.size / 2]
        val rMax = medianMinDist * 1.8

        // 2. Collect local vectors
        val vectors = mutableListOf<Point>()
        for (i in points.indices) {
            for (j in points.indices) {
                if (i == j) continue
                val dx = points[j].x - points[i].x
                val dy = points[j].y - points[i].y
                val d = hypot(dx, dy)
                if (d < rMax) vectors.add(Point(dx, dy))
            }
        }

        // 3. Find dominant angle (modulo 90)
        val angles = vectors.map { (atan2(it.y, it.x) * 180.0 / PI + 360.0) % 90.0 }
        var bestAngle = 0.0
        var maxCount = 0
        for (a in 0..89) {
            val count = angles.count { deltaAngle(it, a.toDouble()) < 4.0 }
            if (count > maxCount) {
                maxCount = count
                bestAngle = a.toDouble()
            }
        }

        // Refine angle
        var sumSin = 0.0
        var sumCos = 0.0
        val inliers = angles.filter { deltaAngle(it, bestAngle) < 4.0 }
        for (a in inliers) {
            sumSin += sin(a * PI / 180.0)
            sumCos += cos(a * PI / 180.0)
        }
        val refinedAngle = if (inliers.isNotEmpty()) (atan2(sumSin, sumCos) * 180.0 / PI + 360.0) % 90.0 else 0.0

        // 4. Estimate spacing X and Y
        val rad = refinedAngle * PI / 180.0
        val axisX = Point(cos(rad), sin(rad))
        val axisY = Point(-sin(rad), cos(rad))

        val spacingsX = mutableListOf<Double>()
        val spacingsY = mutableListOf<Double>()
        for (v in vectors) {
            val px = abs(v.x * axisX.x + v.y * axisX.y)
            val py = abs(v.x * axisY.x + v.y * axisY.y)
            val d = hypot(v.x, v.y)
            if (px > 0.85 * d) spacingsX.add(d)
            if (py > 0.85 * d) spacingsY.add(d)
        }

        val spacingX = if (spacingsX.isNotEmpty()) spacingsX.sorted()[spacingsX.size / 2] else medianMinDist
        val spacingY = if (spacingsY.isNotEmpty()) spacingsY.sorted()[spacingsY.size / 2] else medianMinDist

        // 5. Seed point (closest to centroid)
        val cx = points.map { it.x }.average()
        val cy = points.map { it.y }.average()
        val centroid = Point(cx, cy)
        val seed = points.minByOrNull { hypot(it.x - centroid.x, it.y - centroid.y) } ?: points[0]

        // 6. BFS Assignment
        val assigned = mutableMapOf<Point, Pair<Int, Int>>()
        assigned[seed] = Pair(0, 0)
        val queue = mutableListOf(seed)

        val unvisited = points.toMutableList()
        unvisited.remove(seed)

        while (queue.isNotEmpty()) {
            val p = queue.removeAt(0)
            val pRowCol = assigned[p]!!

            val neighbors = unvisited.filter { hypot(it.x - p.x, it.y - p.y) < rMax * 2.5 }
            for (n in neighbors) {
                val dx = n.x - p.x
                val dy = n.y - p.y

                val angleInv = -rad
                val dxR = dx * cos(angleInv) - dy * sin(angleInv)
                val dyR = dx * sin(angleInv) + dy * cos(angleInv)

                val dcol = Math.round(dxR / spacingX).toInt()
                val drow = Math.round(dyR / spacingY).toInt()

                val expX = dcol * spacingX
                val expY = drow * spacingY

                if (abs(dxR - expX) < 0.35 * spacingX && abs(dyR - expY) < 0.35 * spacingY) {
                    assigned[n] = Pair(pRowCol.first + drow, pRowCol.second + dcol)
                    queue.add(n)
                    unvisited.remove(n)
                }
            }
        }

        val gridPoints = mutableMapOf<Pair<Int, Int>, V6GridPoint>()
        for ((pt, rc) in assigned) {
            gridPoints[rc] = V6GridPoint(
                row = rc.first,
                column = rc.second,
                expectedPosition = pt,
                observedPosition = pt,
                isValid = true,
                confidence = 1.0
            )
        }

        return V6GridModel(
            points = gridPoints,
            originX = seed.x,
            originY = seed.y,
            spacingX = spacingX,
            spacingY = spacingY,
            angleDeg = refinedAngle,
            validCellCount = gridPoints.size
        )
    }

    fun recoverLensGrid(lensPoints: List<Point>, refGrid: V6GridModel): V6GridModel {
        val rawLensGrid = recoverZeroGrid(lensPoints)
        if (rawLensGrid.validCellCount == 0 || refGrid.validCellCount == 0) return rawLensGrid

        var bestDr = 0
        var bestDc = 0
        var minMedianDist = Double.MAX_VALUE

        for (dr in -20..20) {
            for (dc in -20..20) {
                val dists = mutableListOf<Double>()
                for ((rc, pt) in rawLensGrid.points) {
                    val refRc = Pair(rc.first + dr, rc.second + dc)
                    val refPt = refGrid.points[refRc]
                    if (refPt != null) {
                        dists.add(hypot(pt.observedPosition!!.x - refPt.observedPosition!!.x,
                                        pt.observedPosition!!.y - refPt.observedPosition!!.y))
                    }
                }
                if (dists.size >= 3) {
                    dists.sort()
                    val medianDist = dists[dists.size / 2]
                    if (medianDist < minMedianDist) {
                        minMedianDist = medianDist
                        bestDr = dr
                        bestDc = dc
                    }
                }
            }
        }

        val alignedPoints = mutableMapOf<Pair<Int, Int>, V6GridPoint>()
        for ((rc, pt) in rawLensGrid.points) {
            val finalRc = Pair(rc.first + bestDr, rc.second + bestDc)
            val expectedPos = refGrid.points[finalRc]?.expectedPosition ?: pt.observedPosition!!
            alignedPoints[finalRc] = pt.copy(
                row = finalRc.first,
                column = finalRc.second,
                expectedPosition = expectedPos
            )
        }

        return rawLensGrid.copy(
            points = alignedPoints,
            validCellCount = alignedPoints.size
        )
    }
}
