    fun analyzePoints(matchedRef: List<Point>, matchedLens: List<Point>, w: Double, h: Double, baseRefDotCount: Int = 0, baseLensDotCount: Int = 0, spacing: Double = 30.0, rejections: MutableMap<String, Int> = mutableMapOf()): V4RunResult {
        try {
            val cx = w / 2.0
            val cy = h / 2.0
            val rMax = min(w, h) / 2.0
            
            val anchorRef = mutableListOf<Point>()
            val anchorLens = mutableListOf<Point>()
            val measurementRef = mutableListOf<Point>()
            val measurementLens = mutableListOf<Point>()
            
            val innerRadiusSq = (rMax * 0.7) * (rMax * 0.7)
            
            for (i in matchedRef.indices) {
                val pt = matchedRef[i]
                val dSq = (pt.x - cx) * (pt.x - cx) + (pt.y - cy) * (pt.y - cy)
                if (dSq > innerRadiusSq) {
                    anchorRef.add(matchedRef[i])
                    anchorLens.add(matchedLens[i])
                } else {
                    measurementRef.add(matchedRef[i])
                    measurementLens.add(matchedLens[i])
                }
            }
            
            var globalScaleAmbiguous = false
            var registrationRms = 0.0
            var inliersCount = 0
            
            val useRigidFallback = anchorRef.size < 15
            if (useRigidFallback) {
                globalScaleAmbiguous = true
            }
            
            val srcMat = MatOfPoint2f()
            val dstMat = MatOfPoint2f()
            
            if (useRigidFallback) {
                srcMat.fromList(matchedLens)
                dstMat.fromList(matchedRef)
            } else {
                srcMat.fromList(anchorLens)
                dstMat.fromList(anchorRef)
            }
            
            val mask = Mat()
            val transformMat: Mat
            
            val ransacThresh = if (useRigidFallback) max(15.0, spacing * 0.8) else max(5.0, spacing * 0.4)
            
            if (useRigidFallback) {
                if (matchedRef.size < 3) {
                     return V4RunResult(success = false, errorMessage = "Insufficient matched points (<3)", candidateMatches = matchedRef.size, matchRejections = rejections)
                }
                transformMat = Calib3d.estimateAffinePartial2D(srcMat, dstMat, mask, Calib3d.RANSAC, ransacThresh)
            } else {
                if (anchorRef.size < 4) {
                     return V4RunResult(success = false, errorMessage = "Insufficient anchors (<4)", candidateMatches = matchedRef.size, matchRejections = rejections)
                }
                transformMat = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, ransacThresh, mask)
            }
            
            if (transformMat.empty()) {
                rejections["registration_inconsistency"] = rejections.getOrDefault("registration_inconsistency", 0) + matchedRef.size
                return V4RunResult(success = false, errorMessage = "Registration failed", candidateMatches = matchedRef.size, matchRejections = rejections)
            }
            
            val maskArray = ByteArray(mask.rows() * mask.cols())
            mask.get(0, 0, maskArray)
            inliersCount = maskArray.count { it.toInt() != 0 }
            
            val ptsToMeasureRef = mutableListOf<Point>()
            val ptsToMeasureLens = mutableListOf<Point>()
            
            if (useRigidFallback) {
                for (i in matchedRef.indices) {
                    if (maskArray[i].toInt() != 0) {
                        ptsToMeasureRef.add(matchedRef[i])
                        ptsToMeasureLens.add(matchedLens[i])
                    } else {
                        rejections["ransac_rejection"] = rejections.getOrDefault("ransac_rejection", 0) + 1
                    }
                }
            } else {
                for (i in anchorRef.indices) {
                    if (maskArray[i].toInt() == 0) {
                        rejections["ransac_rejection"] = rejections.getOrDefault("ransac_rejection", 0) + 1
                    }
                }
                ptsToMeasureRef.addAll(measurementRef)
                ptsToMeasureLens.addAll(measurementLens)
            }
            
            if (ptsToMeasureRef.size < 6) {
                 return V4RunResult(success = false, errorMessage = "Insufficient measurement points (<6)", candidateMatches = matchedRef.size, acceptedMatches = inliersCount, matchRejections = rejections)
            }
            
            var q1 = false; var q2 = false; var q3 = false; var q4 = false
            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            
            for (pt in ptsToMeasureRef) {
                if (pt.x < cx && pt.y < cy) q1 = true
                if (pt.x >= cx && pt.y < cy) q2 = true
                if (pt.x < cx && pt.y >= cy) q3 = true
                if (pt.x >= cx && pt.y >= cy) q4 = true
                
                if (pt.x < minX) minX = pt.x
                if (pt.x > maxX) maxX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.y > maxY) maxY = pt.y
            }
            
            val quadCount = (if (q1) 1 else 0) + (if (q2) 1 else 0) + (if (q3) 1 else 0) + (if (q4) 1 else 0)
            val spreadX = maxX - minX
            val spreadY = maxY - minY
            val spatialCoveragePct = ((spreadX * spreadY) / (w * h)) * 100.0
            
            if (quadCount < 2 || spreadX < w * 0.1 || spreadY < h * 0.1) {
                rejections["roi_rejection"] = rejections.getOrDefault("roi_rejection", 0) + ptsToMeasureRef.size
                return V4RunResult(success = false, errorMessage = "Poor spatial coverage (quads: $quadCount, spread: ${spreadX.toInt()}x${spreadY.toInt()})", candidateMatches = matchedRef.size, acceptedMatches = ptsToMeasureRef.size, matchRejections = rejections, spatialCoveragePct = spatialCoveragePct, quadrantCoverage = quadCount)
            }
            
            val srcMeasMat = MatOfPoint2f()
            srcMeasMat.fromList(ptsToMeasureLens)
            val dstMeasMat = MatOfPoint2f()
            
            if (useRigidFallback) {
                Core.transform(srcMeasMat, dstMeasMat, transformMat)
            } else {
                Core.perspectiveTransform(srcMeasMat, dstMeasMat, transformMat)
            }
            
            val transformedLens = dstMeasMat.toList()
            
            if (!useRigidFallback) {
                var rSum = 0.0
                val anchorLensTransformed = MatOfPoint2f()
                Core.perspectiveTransform(srcMat, anchorLensTransformed, transformMat)
                val transformedAnchors = anchorLensTransformed.toList()
                for (i in anchorRef.indices) {
                    if (maskArray[i].toInt() != 0) {
                        val dx = transformedAnchors[i].x - anchorRef[i].x
                        val dy = transformedAnchors[i].y - anchorRef[i].y
                        rSum += dx * dx + dy * dy
                    }
                }
                registrationRms = sqrt(rSum / max(1, inliersCount))
            } else {
                var rSum = 0.0
                for (i in ptsToMeasureRef.indices) {
                    val dx = transformedLens[i].x - ptsToMeasureRef[i].x
                    val dy = transformedLens[i].y - ptsToMeasureRef[i].y
                    rSum += dx * dx + dy * dy
                }
                registrationRms = sqrt(rSum / max(1, ptsToMeasureRef.size))
            }
            
            var sumDx = 0.0
            var sumDy = 0.0
            for (i in ptsToMeasureRef.indices) {
                sumDx += (transformedLens[i].x - ptsToMeasureRef[i].x)
                sumDy += (transformedLens[i].y - ptsToMeasureRef[i].y)
            }
            val meanDx = sumDx / ptsToMeasureRef.size
            val meanDy = sumDy / ptsToMeasureRef.size
            
            val A = Mat(ptsToMeasureRef.size, 3, CvType.CV_64F)
            val B = Mat(ptsToMeasureRef.size, 2, CvType.CV_64F)
            
            for (i in ptsToMeasureRef.indices) {
                A.put(i, 0, ptsToMeasureRef[i].x)
                A.put(i, 1, ptsToMeasureRef[i].y)
                A.put(i, 2, 1.0)
                
                B.put(i, 0, transformedLens[i].x - ptsToMeasureRef[i].x)
                B.put(i, 1, transformedLens[i].y - ptsToMeasureRef[i].y)
            }
            
            val W = Mat()
            val U = Mat()
            val Vt = Mat()
            Core.SVDecomp(A, W, U, Vt)
            
            var rank = 0
            var maxSingular = 0.0
            var minSingular = Double.MAX_VALUE
            for (i in 0 until W.rows()) {
                val s = W.get(i, 0)[0]
                if (s > maxSingular) maxSingular = s
                if (s > 1e-6) {
                    rank++
                    if (s < minSingular) minSingular = s
                }
            }
            val cond = if (minSingular > 0.0) maxSingular / minSingular else Double.MAX_VALUE
            
            var degeneracyStatus = "OK"
            if (rank < 3) {
                degeneracyStatus = "RANK_DEFICIENT"
            } else if (cond > 1e4 || cond.isNaN()) {
                degeneracyStatus = "ILL_CONDITIONED"
            }
            
            if (degeneracyStatus != "OK") {
                 return V4RunResult(success = false, errorMessage = "Degenerate geometric configuration: $degeneracyStatus",
                     candidateMatches = matchedRef.size, acceptedMatches = ptsToMeasureRef.size, matrixRank = rank, conditionNumber = cond, degeneracyStatus = degeneracyStatus, matchRejections = rejections)
            }
            
            val J_matrix = Mat()
            try {
                Core.solve(A, B, J_matrix, Core.DECOMP_SVD)
            } catch (e: Exception) {
                return V4RunResult(success = false, errorMessage = "OpenCV solve failed: ${e.message}",
                     candidateMatches = matchedRef.size, acceptedMatches = ptsToMeasureRef.size, matrixRank = rank, conditionNumber = cond, degeneracyStatus = "SOLVE_EXCEPTION", matchRejections = rejections)
            }
            
            val j00 = J_matrix.get(0, 0)[0]
            val j10 = J_matrix.get(0, 1)[0]
            val j01 = J_matrix.get(1, 0)[0]
            val j11 = J_matrix.get(1, 1)[0]
            
            if (j00.isNaN() || j10.isNaN() || j01.isNaN() || j11.isNaN() ||
                j00.isInfinite() || j10.isInfinite() || j01.isInfinite() || j11.isInfinite()) {
                return V4RunResult(success = false, errorMessage = "NaN/Infinity in optical field solution",
                     candidateMatches = matchedRef.size, acceptedMatches = ptsToMeasureRef.size, matrixRank = rank, conditionNumber = cond, degeneracyStatus = "NAN_INF", matchRejections = rejections)
            }
            
            var fieldFitRmsSum = 0.0
            for (i in ptsToMeasureRef.indices) {
                val x = ptsToMeasureRef[i].x
                val y = ptsToMeasureRef[i].y
                val u = j00 * x + j01 * y + J_matrix.get(2, 0)[0]
                val v = j10 * x + j11 * y + J_matrix.get(2, 1)[0]
                
                val dx = transformedLens[i].x - ptsToMeasureRef[i].x
                val dy = transformedLens[i].y - ptsToMeasureRef[i].y
                
                val diffU = u - dx
                val diffV = v - dy
                fieldFitRmsSum += diffU * diffU + diffV * diffV
            }
            val fieldFitRms = sqrt(fieldFitRmsSum / ptsToMeasureRef.size)
            
            val maxAllowedRms = max(2.0, spacing * 0.15)
            if (fieldFitRms > maxAllowedRms) {
                rejections["low_confidence"] = rejections.getOrDefault("low_confidence", 0) + ptsToMeasureRef.size
                return V4RunResult(success = false, errorMessage = "Poor field fit (RMS $fieldFitRms > $maxAllowedRms)",
                     candidateMatches = matchedRef.size, acceptedMatches = ptsToMeasureRef.size, matrixRank = rank, conditionNumber = cond, degeneracyStatus = "POOR_FIT", matchRejections = rejections, fieldFitRms = fieldFitRms)
            }

            val s00 = j00
            val s11 = j11
            val s01 = 0.5 * (j01 + j10)
            
            val trace = s00 + s11
            val det = s00 * s11 - s01 * s01
            
            val lambda1 = trace / 2.0 + sqrt(max(0.0, (trace * trace) / 4.0 - det))
            val lambda2 = trace / 2.0 - sqrt(max(0.0, (trace * trace) / 4.0 - det))
            
            val dirX = lambda1 - s11
            val dirY = s01
            val angleRad = atan2(dirY, dirX)
            var axis = (angleRad * 180.0 / Math.PI)
            if (axis < 0) axis += 180.0
            if (axis >= 180.0) axis -= 180.0
            
            val isotropic = (lambda1 + lambda2) / 2.0
            val anisotropic = abs(lambda1 - lambda2)
            
            return V4RunResult(
                success = true,
                axis = axis,
                lambda1 = lambda1,
                lambda2 = lambda2,
                isotropic = isotropic,
                anisotropic = anisotropic,
                trackedDots = ptsToMeasureRef.size,
                registrationRms = registrationRms,
                ransacInliers = inliersCount,
                fieldFitRms = fieldFitRms,
                refDotCount = baseRefDotCount,
                lensDotCount = baseLensDotCount,
                meanDx = meanDx,
                meanDy = meanDy,
                referencePoints = ptsToMeasureRef,
                observedPoints = transformedLens,
                refWidth = w.toInt(),
                refHeight = h.toInt(),
                globalScaleAmbiguous = globalScaleAmbiguous,
                candidateMatches = matchedRef.size,
                acceptedMatches = ptsToMeasureRef.size,
                rejectedMatches = matchedRef.size - ptsToMeasureRef.size,
                matrixRank = rank,
                conditionNumber = cond,
                degeneracyStatus = degeneracyStatus,
                matchRejections = rejections,
                spatialCoveragePct = spatialCoveragePct,
                quadrantCoverage = quadCount
            )
        } catch (e: Exception) {
            Log.e("V4OpticalAnalyzer", "AnalyzePoints failed", e)
            return V4RunResult(success = false, errorMessage = "Exception: ${e.message}", degeneracyStatus = "EXCEPTION", matchRejections = rejections)
        }
    }
