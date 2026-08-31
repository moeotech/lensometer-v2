package com.example.analysis

import androidx.camera.core.ImageProxy
import com.example.model.*
import kotlin.math.sqrt
import kotlin.math.abs

object OpticalAnalyzer {

    fun analyzeFrame(
        imageProxy: ImageProxy,
        frameIndex: Int,
        isFlashOn: Boolean,
        isWithLens: Boolean
    ): FrameMeasurement {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        buffer.rewind()
        val totalPixels = width * height
        val yValues = IntArray(totalPixels)

        var sumLuminance = 0f
        var maxLum = 0
        var brightCount = 0

        // Region sums and counts
        var centerSum = 0f; var centerCount = 0
        var leftSum = 0f; var leftCount = 0
        var rightSum = 0f; var rightCount = 0
        var topSum = 0f; var topCount = 0
        var bottomSum = 0f; var bottomCount = 0

        val halfW = width / 2
        val halfH = height / 2
        val qtrW = width / 4
        val qtrH = height / 4

        for (y in 0 until height) {
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                val pixelIndex = rowOffset + x * pixelStride
                if (pixelIndex < buffer.capacity()) {
                    val v = buffer.get(pixelIndex).toInt() and 0xFF
                    yValues[y * width + x] = v
                    sumLuminance += v
                    if (v > maxLum) maxLum = v
                    if (v > 200) brightCount++

                    // Regions
                    if (x in (halfW - qtrW)..(halfW + qtrW) && y in (halfH - qtrH)..(halfH + qtrH)) {
                        centerSum += v
                        centerCount++
                    }
                    if (x < halfW) {
                        leftSum += v
                        leftCount++
                    } else {
                        rightSum += v
                        rightCount++
                    }
                    if (y < halfH) {
                        topSum += v
                        topCount++
                    } else {
                        bottomSum += v
                        bottomCount++
                    }
                }
            }
        }

        val avgLum = if (totalPixels > 0) sumLuminance / totalPixels else 0f

        // Standard deviation
        var varianceSum = 0f
        for (i in 0 until totalPixels) {
            val diff = yValues[i] - avgLum
            varianceSum += diff * diff
        }
        val stdDev = if (totalPixels > 0) sqrt(varianceSum / totalPixels) else 0f

        val brightPct = if (totalPixels > 0) (brightCount.toFloat() / totalPixels) * 100f else 0f

        val centerLum = if (centerCount > 0) centerSum / centerCount else 0f
        val leftLum = if (leftCount > 0) leftSum / leftCount else 0f
        val rightLum = if (rightCount > 0) rightSum / rightCount else 0f
        val topLum = if (topCount > 0) topSum / topCount else 0f
        val bottomLum = if (bottomCount > 0) bottomSum / bottomCount else 0f

        // Sharpness (Laplacian variance approximation)
        var laplacianSum = 0f
        var laplacianSqSum = 0f
        val sampleStep = 2 // step for performance
        var lapCount = 0
        for (y in 1 until height - 1 step sampleStep) {
            for (x in 1 until width - 1 step sampleStep) {
                val idx = y * width + x
                val center = yValues[idx]
                val up = yValues[(y - 1) * width + x]
                val down = yValues[(y + 1) * width + x]
                val left = yValues[y * width + (x - 1)]
                val right = yValues[y * width + (x + 1)]

                val lap = abs(4 * center - (up + down + left + right)).toFloat()
                laplacianSum += lap
                laplacianSqSum += lap * lap
                lapCount++
            }
        }
        val avgLap = if (lapCount > 0) laplacianSum / lapCount else 0f
        val sharpnessVariance = if (lapCount > 0) (laplacianSqSum / lapCount) - (avgLap * avgLap) else 0f

        // Reflection detection for flash ON frames
        val reflectionCandidates = mutableListOf<ReflectionCandidate>()
        if (isFlashOn) {
            // Simple bright blob detector
            val threshold = (avgLum + 2.0f * stdDev).coerceIn(200f, 250f).toInt()
            val visited = BooleanArray(totalPixels)
            var candidateId = 1

            for (y in 0 until height step 4) {
                for (x in 0 until width step 4) {
                    val idx = y * width + x
                    if (!visited[idx] && yValues[idx] > threshold) {
                        // Flood fill / cluster
                        var blobArea = 0
                        var blobSum = 0
                        var blobPeak = 0
                        var minX = x; var maxX = x
                        var minY = y; var maxY = y
                        var sumX = 0f; var sumY = 0f

                        val queue = mutableListOf<Pair<Int, Int>>()
                        queue.add(Pair(x, y))
                        visited[idx] = true

                        var head = 0
                        while (head < queue.size && blobArea < 5000) {
                            val (cx, cy) = queue[head++]
                            blobArea++
                            val v = yValues[cy * width + cx]
                            blobSum += v
                            if (v > blobPeak) blobPeak = v
                            sumX += cx
                            sumY += cy

                            if (cx < minX) minX = cx
                            if (cx > maxX) maxX = cx
                            if (cy < minY) minY = cy
                            if (cy > maxY) maxY = cy

                            // Check neighbors
                            val neighbors = arrayOf(
                                Pair(cx + 2, cy), Pair(cx - 2, cy),
                                Pair(cx, cy + 2), Pair(cx, cy - 2)
                            )
                            for (n in neighbors) {
                                if (n.first in 0 until width && n.second in 0 until height) {
                                    val nIdx = n.second * width + n.first
                                    if (!visited[nIdx] && yValues[nIdx] > threshold) {
                                        visited[nIdx] = true
                                        queue.add(n)
                                    }
                                }
                            }
                        }

                        if (blobArea > 10) { // minimum blob size
                            val centroidX = sumX / blobArea
                            val centroidY = sumY / blobArea
                            val avgBright = blobSum.toFloat() / blobArea
                            reflectionCandidates.add(
                                ReflectionCandidate(
                                    id = candidateId++,
                                    centroidX = centroidX,
                                    centroidY = centroidY,
                                    area = blobArea,
                                    peakBrightness = blobPeak,
                                    avgBrightness = avgBright,
                                    boundingBox = BoundingBox(minX, maxX, minY, maxY)
                                )
                            )
                        }
                    }
                }
            }
        }

        val flashSignal = maxLum.toFloat() - avgLum

        return FrameMeasurement(
            frameIndex = frameIndex,
            timestamp = System.currentTimeMillis(),
            isFlashOn = isFlashOn,
            isWithLens = isWithLens,
            avgLuminance = avgLum,
            maxLuminance = maxLum,
            stdDevLuminance = stdDev,
            brightPixelCount = brightCount,
            brightPixelPercentage = brightPct,
            centerLuminance = centerLum,
            leftLuminance = leftLum,
            rightLuminance = rightLum,
            topLuminance = topLum,
            bottomLuminance = bottomLum,
            sharpness = sharpnessVariance.coerceAtLeast(0f),
            flashSignal = flashSignal,
            reflectionCandidates = reflectionCandidates
        )
    }

    fun summarizePhase(phaseName: String, frames: List<FrameMeasurement>): PhaseStats {
        val count = frames.size
        if (count == 0) return PhaseStats(phaseName, emptyList(), 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        val meanLum = frames.map { it.avgLuminance }.average().toFloat()
        val meanSharp = frames.map { it.sharpness }.average().toFloat()
        val meanBrightPx = frames.map { it.brightPixelPercentage }.average().toFloat()
        val meanFlash = frames.map { it.flashSignal }.average().toFloat()
        val meanRefs = frames.map { it.reflectionCandidates.size.toFloat() }.average().toFloat()

        val stdDevLum = sqrt(frames.map { (it.avgLuminance - meanLum) * (it.avgLuminance - meanLum) }.average()).toFloat()
        val stdDevSharp = sqrt(frames.map { (it.sharpness - meanSharp) * (it.sharpness - meanSharp) }.average()).toFloat()
        val stdDevBrightPx = sqrt(frames.map { (it.brightPixelPercentage - meanBrightPx) * (it.brightPixelPercentage - meanBrightPx) }.average()).toFloat()
        val stdDevFlash = sqrt(frames.map { (it.flashSignal - meanFlash) * (it.flashSignal - meanFlash) }.average()).toFloat()
        val stdDevRefs = sqrt(frames.map { (it.reflectionCandidates.size - meanRefs) * (it.reflectionCandidates.size - meanRefs) }.average()).toFloat()

        fun cv(stdDev: Float, mean: Float) = if (mean != 0f) stdDev / mean else 0f

        return PhaseStats(
            phaseName = phaseName,
            frames = frames,
            meanLuminance = meanLum,
            stdDevLuminance = stdDevLum,
            meanSharpness = meanSharp,
            stdDevSharpness = stdDevSharp,
            meanBrightPixels = meanBrightPx,
            stdDevBrightPixels = stdDevBrightPx,
            meanFlashSignal = meanFlash,
            stdDevFlashSignal = stdDevFlash,
            meanReflections = meanRefs,
            stdDevReflections = stdDevRefs,
            cvLuminance = cv(stdDevLum, meanLum),
            cvSharpness = cv(stdDevSharp, meanSharp),
            cvBrightPixels = cv(stdDevBrightPx, meanBrightPx),
            cvFlashSignal = cv(stdDevFlash, meanFlash),
            cvReflections = cv(stdDevRefs, meanRefs)
        )
    }

    fun comparePhases(
        noLens1: PhaseStats,
        noLens2: PhaseStats,
        lens1: PhaseStats,
        lens2: PhaseStats
    ): ExperimentReport {
        // Repeatability: absolute difference between TEST 1 and TEST 2 means
        val noLensLumDiff = abs(noLens1.meanLuminance - noLens2.meanLuminance)
        val noLensSharpDiff = abs(noLens1.meanSharpness - noLens2.meanSharpness)
        val noLensRepeatability = noLensLumDiff + noLensSharpDiff // composite metric

        val lensLumDiff = abs(lens1.meanLuminance - lens2.meanLuminance)
        val lensSharpDiff = abs(lens1.meanSharpness - lens2.meanSharpness)
        val lensRepeatability = lensLumDiff + lensSharpDiff

        val combinedNoLensLum = (noLens1.meanLuminance + noLens2.meanLuminance) / 2f
        val combinedLensLum = (lens1.meanLuminance + lens2.meanLuminance) / 2f
        val combinedNoLensSharp = (noLens1.meanSharpness + noLens2.meanSharpness) / 2f
        val combinedLensSharp = (lens1.meanSharpness + lens2.meanSharpness) / 2f
        val combinedNoLensRefs = (noLens1.meanReflections + noLens2.meanReflections) / 2f
        val combinedLensRefs = (lens1.meanReflections + lens2.meanReflections) / 2f

        val betweenStateLum = abs(combinedLensLum - combinedNoLensLum)
        val betweenStateSharp = abs(combinedLensSharp - combinedNoLensSharp)
        val betweenStateDifference = betweenStateLum + betweenStateSharp

        // A rudimentary heuristic for detection
        val opticalDifferenceDetected = betweenStateDifference > (noLensRepeatability + lensRepeatability) * 1.5f || abs(combinedLensRefs - combinedNoLensRefs) > 5

        val observation = if (opticalDifferenceDetected) {
            "Optical difference detected. Signal is stronger than repeatability noise floor. Reflection candidate difference observed."
        } else {
            "Optical difference not detected or falls within the repeatability noise floor."
        }

        return ExperimentReport(
            timestamp = System.currentTimeMillis(),
            noLens1 = noLens1,
            noLens2 = noLens2,
            lens1 = lens1,
            lens2 = lens2,
            noLensRepeatability = noLensRepeatability,
            lensRepeatability = lensRepeatability,
            betweenStateDifference = betweenStateDifference,
            opticalDifferenceDetected = opticalDifferenceDetected,
            observationText = observation
        )
    }
}
