    suspend fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>): V4RunResult = withContext(Dispatchers.Default) {
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return@withContext V4RunResult(success = false, errorMessage = "Missing frames")
        }
        
        try {
            val refAgg = aggregateFrames(noLensFrames)
            if (!refAgg.success) {
                return@withContext V4RunResult(success = false, errorMessage = "Ref aggregation failed: ${refAgg.errorMessage}")
            }
            val baseRefPoints = refAgg.points
            
            val lensAgg = aggregateFrames(withLensFrames)
            if (!lensAgg.success) {
                return@withContext V4RunResult(success = false, errorMessage = "Lens aggregation failed: ${lensAgg.errorMessage}")
            }
            val baseLensPoints = lensAgg.points
            
            val spacing = estimateSpacing(baseRefPoints)
            
            val rejections = mutableMapOf(
                "geometric_gate" to 0,
                "distance_gate" to 0,
                "ransac_rejection" to 0,
                "roi_rejection" to 0,
                "duplicate_match" to 0,
                "registration_inconsistency" to 0,
                "low_confidence" to 0,
                "other" to 0
            )
            
            val binSize = spacing * 0.2
            val histogram = HashMap<Pair<Int, Int>, Int>()
            for (ptRef in baseRefPoints) {
                for (ptLens in baseLensPoints) {
                    val dx = ptLens.x - ptRef.x
                    val dy = ptLens.y - ptRef.y
                    if (hypot(dx, dy) < 200.0) {
                        val binX = (dx / binSize).roundToInt()
                        val binY = (dy / binSize).roundToInt()
                        val key = Pair(binX, binY)
                        histogram[key] = histogram.getOrDefault(key, 0) + 1
                    }
                }
            }
            
            var bestBin = Pair(0, 0)
            var maxCount = 0
            for ((bin, count) in histogram) {
                if (count > maxCount) {
                    maxCount = count
                    bestBin = bin
                }
            }
            
            val globalTx = bestBin.first * binSize
            val globalTy = bestBin.second * binSize
            
            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()
            val usedLens = mutableSetOf<Int>()
            
            for (ptRef in baseRefPoints) {
                val shiftedX = ptRef.x + globalTx
                val shiftedY = ptRef.y + globalTy
                
                var bestDist = Double.MAX_VALUE
                var bestIdx = -1
                for (j in baseLensPoints.indices) {
                    val ptLens = baseLensPoints[j]
                    val dist = hypot(ptLens.x - shiftedX, ptLens.y - shiftedY)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = j
                    }
                }
                
                if (bestIdx != -1) {
                    if (bestDist < spacing * 0.6) {
                        if (usedLens.contains(bestIdx)) {
                            rejections["duplicate_match"] = rejections.getOrDefault("duplicate_match", 0) + 1
                        } else {
                            usedLens.add(bestIdx)
                            candidateRef.add(ptRef)
                            candidateLens.add(baseLensPoints[bestIdx])
                        }
                    } else {
                        rejections["distance_gate"] = rejections.getOrDefault("distance_gate", 0) + 1
                    }
                } else {
                    rejections["other"] = rejections.getOrDefault("other", 0) + 1
                }
            }
            
            val w = noLensFrames[0].width.toDouble()
            val h = noLensFrames[0].height.toDouble()
            
            val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections)
            
            return@withContext res.copy(
                framesCaptured = withLensFrames.size,
                framesAccepted = lensAgg.accepted,
                framesRejected = lensAgg.rejected,
                temporalTrackCount = lensAgg.points.size,
                stableTrackCount = lensAgg.stableTrackCount,
                medianTrackLifetime = lensAgg.medianTrackLifetime
            )
            
        } catch (e: Exception) {
            Log.e("V4OpticalAnalyzer", "Analyze failed", e)
            return@withContext V4RunResult(success = false, errorMessage = "Exception: ${e.message}")
        }
    }
