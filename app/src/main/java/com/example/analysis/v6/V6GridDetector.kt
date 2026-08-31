package com.example.analysis.v6

import org.opencv.core.Point
import kotlin.math.*

object V6GridDetector {

    private fun findClosestPoint(expected: Point, points: List<Point>, maxDist: Double): Point? {
        var closest: Point? = null
        var minDistSq = maxDist * maxDist
        for (pt in points) {
            val dx = pt.x - expected.x
            val dy = pt.y - expected.y
            val distSq = dx * dx + dy * dy
            if (distSq < minDistSq) {
                minDistSq = distSq
                closest = pt
            }
        }
        return closest
    }

    fun detectGrid(points: List<Point>, targetDef: V6TargetDefinition): V6GridModel {
        if (points.isEmpty()) {
            return V6GridModel(emptyMap(), targetDef.centerRow, targetDef.centerColumn, 0.0, 0)
        }

        var sumX = 0.0
        var sumY = 0.0
        for (pt in points) {
            sumX += pt.x
            sumY += pt.y
        }
        val center = Point(sumX / points.size, sumY / points.size)
        
        val spacings = mutableListOf<Double>()
        for (i in points.indices) {
            var minD = Double.MAX_VALUE
            for (j in points.indices) {
                if (i == j) continue
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dSq = dx * dx + dy * dy
                if (dSq < minD) minD = dSq
            }
            spacings.add(sqrt(minD))
        }
        spacings.sort()
        val medianSpacing = if (spacings.isNotEmpty()) spacings[spacings.size / 2] else 10.0

        val gridPoints = mutableMapOf<Pair<Int, Int>, V6GridPoint>()
        var validCount = 0

        val xStep = medianSpacing
        val yStep = medianSpacing
        val orientation = 0.0

        val maxSearchRadius = medianSpacing * 0.4
        for (row in 0 until targetDef.rowCount) {
            for (col in 0 until targetDef.columnCount) {
                val dRow = row - targetDef.centerRow
                val dCol = col - targetDef.centerColumn
                
                val expectedX = center.x + dCol * xStep
                val expectedY = center.y + dRow * yStep
                val expectedPos = Point(expectedX, expectedY)
                
                val observedPos = findClosestPoint(expectedPos, points, maxSearchRadius)
                val isValid = observedPos != null
                if (isValid) validCount++
                
                gridPoints[Pair(row, col)] = V6GridPoint(
                    row = row,
                    column = col,
                    expectedPosition = expectedPos,
                    observedPosition = observedPos,
                    isValid = isValid,
                    confidence = if (isValid) 1.0 else 0.0
                )
            }
        }

        return V6GridModel(
            points = gridPoints,
            centerRow = targetDef.centerRow,
            centerColumn = targetDef.centerColumn,
            orientationAngle = orientation,
            validCellCount = validCount
        )
    }
}
