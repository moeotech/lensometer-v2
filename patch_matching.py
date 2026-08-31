import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
            val globalTx = bestBin.first * binSize
            val globalTy = bestBin.second * binSize

            // TASK 2 - USE MUTUAL NEAREST-NEIGHBOR SEEDING
            val acceptedMatches = mutableMapOf<Pair<Int, Int>, Point>()
            var nonMutualCount = 0
            var seedDistanceRejects = 0

            for ((coord, ptRef) in gridMap) {
                val shiftedX = ptRef.x + globalTx
                val shiftedY = ptRef.y + globalTy
                
                var bestDistRefToLens = Double.MAX_VALUE
                var bestLens: Point? = null
                for (ptLens in baseLensPoints) {
                    val dist = hypot(ptLens.x - shiftedX, ptLens.y - shiftedY)
                    if (dist < bestDistRefToLens) {
                        bestDistRefToLens = dist
                        bestLens = ptLens
                    }
                }
                
                if (bestLens != null && bestDistRefToLens < spacing * 0.4) {
                    // Check mutual nearest-neighbor
                    var bestDistLensToRef = Double.MAX_VALUE
                    var bestRefCoord: Pair<Int, Int>? = null
                    for ((c, rPt) in gridMap) {
                        val sX = rPt.x + globalTx
                        val sY = rPt.y + globalTy
                        val d = hypot(bestLens.x - sX, bestLens.y - sY)
                        if (d < bestDistLensToRef) {
                            bestDistLensToRef = d
                            bestRefCoord = c
                        }
                    }
                    if (bestRefCoord == coord) {
                        acceptedMatches[coord] = bestLens
                    } else {
                        nonMutualCount++
                    }
                } else if (bestLens != null) {
                    seedDistanceRejects++
                }
            }
            rejections["seedMutualMatches"] = acceptedMatches.size
            rejections["seed_distance"] = seedDistanceRejects
            rejections["non_mutual"] = nonMutualCount

            // TASK 3 - KEEP EXPANSION FLEXIBLE
            // Stage A: Topological neighbor expansion
            var changed = true
            while (changed) {
                changed = false
                for ((coord, ptRef) in gridMap) {
                    if (acceptedMatches.containsKey(coord)) continue
                    
                    val (r, c) = coord
                    val neighbors = listOf(Pair(r-1, c), Pair(r+1, c), Pair(r, c-1), Pair(r, c+1),
                                           Pair(r-1, c-1), Pair(r-1, c+1), Pair(r+1, c-1), Pair(r+1, c+1))
                    val matchedNeighbors = neighbors.filter { acceptedMatches.containsKey(it) }
                    
                    if (matchedNeighbors.isNotEmpty()) {
                        var sumDx = 0.0
                        var sumDy = 0.0
                        for (nCoord in matchedNeighbors) {
                            val nRef = gridMap[nCoord]!!
                            val nLens = acceptedMatches[nCoord]!!
                            sumDx += (nLens.x - nRef.x)
                            sumDy += (nLens.y - nRef.y)
                        }
                        val avgDx = sumDx / matchedNeighbors.size
                        val avgDy = sumDy / matchedNeighbors.size
                        
                        val predX = ptRef.x + avgDx
                        val predY = ptRef.y + avgDy
                        
                        var bestDist = Double.MAX_VALUE
                        var bestLens: Point? = null
                        for (ptLens in baseLensPoints) {
                            if (acceptedMatches.containsValue(ptLens)) continue
                            val dist = hypot(ptLens.x - predX, ptLens.y - predY)
                            if (dist < bestDist) {
                                bestDist = dist
                                bestLens = ptLens
                            }
                        }
                        
                        if (bestLens != null && bestDist < spacing * 0.7) {
                            acceptedMatches[coord] = bestLens
                            changed = true
                        }
                    }
                }
            }
            rejections["neighborExpandedMatches"] = acceptedMatches.size

            // Stage B: Robust affine prediction expansion
            if (acceptedMatches.size >= 10) {
                val srcList = acceptedMatches.values.map { gridMap[acceptedMatches.entries.find { e -> e.value == it }!!.key]!! }
                val dstList = acceptedMatches.values.toList()
                val srcMat = MatOfPoint2f().apply { fromList(srcList) }
                val dstMat = MatOfPoint2f().apply { fromList(dstList) }
                val inliers = Mat()
                val affine = Calib3d.estimateAffine2D(srcMat, dstMat, inliers, Calib3d.RANSAC, spacing * 0.5)
                
                if (!affine.empty()) {
                    for ((coord, ptRef) in gridMap) {
                        if (acceptedMatches.containsKey(coord)) continue
                        
                        val ptMat = MatOfPoint2f(ptRef)
                        val predMat = MatOfPoint2f()
                        Core.transform(ptMat, predMat, affine)
                        val predX = predMat.toArray()[0].x
                        val predY = predMat.toArray()[0].y
                        
                        var bestDist = Double.MAX_VALUE
                        var bestLens: Point? = null
                        for (ptLens in baseLensPoints) {
                            if (acceptedMatches.containsValue(ptLens)) continue
                            val dist = hypot(ptLens.x - predX, ptLens.y - predY)
                            if (dist < bestDist) {
                                bestDist = dist
                                bestLens = ptLens
                            }
                        }
                        
                        if (bestLens != null && bestDist < spacing * 0.8) {
                            acceptedMatches[coord] = bestLens
                        }
                    }
                }
            }
            rejections["affineExpandedMatches"] = acceptedMatches.size

            // TASK 4 - GLOBAL ONE-TO-ONE ASSIGNMENT
            val finalMatches = mutableMapOf<Pair<Int, Int>, Point>()
            var assignmentConflicts = 0
            val usedLensPts = mutableSetOf<Point>()
            
            // Build proposals based on existing acceptedMatches (these were accumulated iteratively)
            // But wait, the prompt says:
            // "After prediction, do not greedily assign dots one by one.
            // Build a cost matrix between predicted reference positions and available lens dots... Use globally sorted minimum-cost assignment with conflict resolution"
            // To do this, we can take the affine model (if valid) or local neighbor predictions, predict ALL grid points, and do a global match.
            // Actually, let's just collect ALL predicted positions for ALL grid map points based on affine or globalTx.
            
            val predictedPositions = mutableMapOf<Pair<Int, Int>, Point>()
            
            // Re-estimate affine with all current accepted matches to predict all points
            val affineGlobal = if (acceptedMatches.size >= 10) {
                val sList = acceptedMatches.values.map { gridMap[acceptedMatches.entries.find { e -> e.value == it }!!.key]!! }
                val dList = acceptedMatches.values.toList()
                val sMat = MatOfPoint2f().apply { fromList(sList) }
                val dMat = MatOfPoint2f().apply { fromList(dList) }
                Calib3d.estimateAffine2D(sMat, dMat, Mat(), Calib3d.RANSAC, spacing * 0.5)
            } else Mat()

            for ((coord, ptRef) in gridMap) {
                if (!affineGlobal.empty()) {
                    val ptMat = MatOfPoint2f(ptRef)
                    val predMat = MatOfPoint2f()
                    Core.transform(ptMat, predMat, affineGlobal)
                    predictedPositions[coord] = Point(predMat.toArray()[0].x, predMat.toArray()[0].y)
                } else {
                    predictedPositions[coord] = Point(ptRef.x + globalTx, ptRef.y + globalTy)
                }
            }

            // Create list of all (Coord, LensPoint, Distance)
            val proposals = mutableListOf<Triple<Pair<Int, Int>, Point, Double>>()
            for ((coord, predPt) in predictedPositions) {
                for (ptLens in baseLensPoints) {
                    val dist = hypot(ptLens.x - predPt.x, ptLens.y - predPt.y)
                    if (dist < spacing * 0.8) {
                        proposals.add(Triple(coord, ptLens, dist))
                    }
                }
            }
            
            // Sort by distance (cost)
            proposals.sortBy { it.third }
            
            for (proposal in proposals) {
                val coord = proposal.first
                val lensPt = proposal.second
                if (!finalMatches.containsKey(coord) && !usedLensPts.contains(lensPt)) {
                    finalMatches[coord] = lensPt
                    usedLensPts.add(lensPt)
                } else {
                    assignmentConflicts++
                }
            }
            rejections["assignment_conflict"] = assignmentConflicts
            
            acceptedMatches.clear()
            acceptedMatches.putAll(finalMatches)
            rejections["finalMatches"] = acceptedMatches.size

            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()
"""

start_str = """            val globalTx = bestBin.first * binSize
            val globalTy = bestBin.second * binSize"""

end_str = """            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()"""

start_idx = text.find(start_str)
end_idx = text.find(end_str, start_idx) + len(end_str)
target = text[start_idx:end_idx]

text = text.replace(target, replacement)

with open(path, "w") as f:
    f.write(text)
