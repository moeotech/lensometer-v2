package com.example.analysis

import android.graphics.Bitmap
import com.example.model.LensMeasurementResult
import com.example.model.DisplacementVector
import com.example.model.LensGeometry
import com.example.model.PointCoord
import com.example.model.MatchPairData
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object LensAnalyzer {
    
    fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>, lensGeom: LensGeometry?): LensMeasurementResult {
        val width = if (noLensFrames.isNotEmpty()) noLensFrames[0].width else 1
        val height = if (noLensFrames.isNotEmpty()) noLensFrames[0].height else 1
        val cx = width / 2.0
        val cy = height / 2.0
        val lensRadius = max(lensGeom?.width ?: 0.0, lensGeom?.height ?: 0.0) / 2.0

        if (noLensFrames.isEmpty() || withLensFrames.isEmpty() || lensGeom == null) {
            val err = if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) "LOW - Missing Frames" else "LOW - Missing Lens Geometry"
            return LensMeasurementResult(
                analysisSuccess = false,
                analysisError = err,
                measurementQualityPass = false,
                qualityReason = err,
                sph = null, cyl = null, axis = null, calibrated = false,
                principal1 = null, principal2 = null, isotropic = null, anisotropic = null,
                principalAngle1 = null, principalAngle2 = null,
                confidence = "NONE",
                registrationRms = null, ransacInliers = null,
                trackedPoints = 0, referencePoints = 0, coverage = 0,
                totalReferenceDots = 0, outerReferenceDotsCount = 0, innerReferenceDotsCount = 0,
                totalLensDots = 0, outerLensDotsCount = 0, innerLensDotsCount = 0,
                candidateOuterMatches = 0, acceptedOuterMatches = 0, transformType = "None",
                innerReferenceCandidatesCount = 0, innerLensCandidatesCount = 0,
                mutualNearestNeighborMatches = 0, rejectedByDistance = 0, rejectedByTopology = 0,
                rejectedByDuplicateAssignment = 0, rejectedByQuadrantRoi = 0, rejectedByGeometricConsistency = 0,
                q1Matches = 0, q2Matches = 0, q3Matches = 0, q4Matches = 0,
                referenceInnerPoints = emptyList(), lensInnerPoints = emptyList(),
                acceptedMatchesList = emptyList(), rejectedReferencePoints = emptyList(), rejectedLensPoints = emptyList(),
                opticalCenterX = null, opticalCenterY = null,
                meanDx = null, meanDy = null,
                imageWidth = width, imageHeight = height,
                geometricCenterX = lensGeom?.centerX ?: cx, geometricCenterY = lensGeom?.centerY ?: cy,
                lensRadius = lensRadius,
                vectors = emptyList()
            )
        }

        // 1. Multi-frame averaging / median of dots
        val refDots = extractStableDots(noLensFrames)
        val testDotsLocal = extractStableDots(withLensFrames)
        
        val totalReferenceDots = refDots.size
        val totalLensDots = testDotsLocal.size

        val angleRad = lensGeom.rotationAngle * PI / 180.0
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val rx = lensGeom.width / 2.0
        val ry = lensGeom.height / 2.0
        
        fun getNormalizedRadiusSq(p: Point): Double {
            val dx = p.x - lensGeom.centerX
            val dy = p.y - lensGeom.centerY
            val localX = dx * cosA + dy * sinA
            val localY = -dx * sinA + dy * cosA
            val nx = if (rx > 0) localX / rx else 0.0
            val ny = if (ry > 0) localY / ry else 0.0
            return nx * nx + ny * ny
        }

        // Split dots
        val refInner = mutableListOf<Point>(); val refOuter = mutableListOf<Point>()
        for (p in refDots) {
            val rSq = getNormalizedRadiusSq(p)
            if (rSq < 0.81) refInner.add(p) // 0.9^2
            else if (rSq > 1.21) refOuter.add(p) // 1.1^2
        }
        
        val innerReferenceDotsCount = refInner.size
        val outerReferenceDotsCount = refOuter.size

        val testInnerLocal = mutableListOf<Point>(); val testOuterLocal = mutableListOf<Point>()
        for (p in testDotsLocal) {
            val rSq = getNormalizedRadiusSq(p)
            if (rSq < 0.81) testInnerLocal.add(p)
            else if (rSq > 1.21) testOuterLocal.add(p)
        }
        
        val innerLensDotsCount = testInnerLocal.size
        val outerLensDotsCount = testOuterLocal.size

        // 2. Global Registration (using REAL OpenCV Homography)
        var registrationRms: Double? = null
        var inliersCount: Int? = null
        var homography = Mat.eye(3, 3, CvType.CV_64F)
        var candidateOuterMatches = 0
        var acceptedOuterMatches = 0
        var transformType = "Identity / Fallback"
        
        if (refOuter.size >= 4 && testOuterLocal.size >= 4) {
            val matchedSrc = mutableListOf<Point>()
            val matchedDst = mutableListOf<Point>()
            
            for (tp in testOuterLocal) {
                var bestD = 30.0; var bestRp: Point? = null
                for (rp in refOuter) {
                    val d = hypot(tp.x - rp.x, tp.y - rp.y)
                    if (d < bestD) { bestD = d; bestRp = rp }
                }
                if (bestRp != null) {
                    candidateOuterMatches++
                    var bestD2 = 30.0; var bestTp: Point? = null
                    for (tp2 in testOuterLocal) {
                        val d2 = hypot(tp2.x - bestRp.x, tp2.y - bestRp.y)
                        if (d2 < bestD2) { bestD2 = d2; bestTp = tp2 }
                    }
                    if (bestTp == tp) {
                        acceptedOuterMatches++
                        matchedSrc.add(tp)
                        matchedDst.add(bestRp)
                    }
                }
            }
            
            if (matchedSrc.size >= 4) {
                val srcMat = MatOfPoint2f(*matchedSrc.toTypedArray())
                val dstMat = MatOfPoint2f(*matchedDst.toTypedArray())
                val mask = Mat()
                
                val H = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, 5.0, mask)
                
                if (!H.empty()) {
                    homography = H
                    val inliers = Core.countNonZero(mask)
                    inliersCount = inliers
                    transformType = "Homography (RANSAC)"
                    var sqErr = 0.0
                    val maskArray = ByteArray(mask.rows() * mask.cols())
                    mask.get(0, 0, maskArray)
                    val transformed = MatOfPoint2f()
                    Core.perspectiveTransform(srcMat, transformed, homography)
                    val transArr = transformed.toArray()
                    for (i in matchedSrc.indices) {
                        if (maskArray[i].toInt() != 0) {
                            sqErr += hypot(transArr[i].x - matchedDst[i].x, transArr[i].y - matchedDst[i].y).pow(2)
                        }
                    }
                    registrationRms = if (inliers > 0) sqrt(sqErr / inliers) else 0.0
                }
            }
        }
        
        // Warp all test inner dots
        val testInner = mutableListOf<Point>()
        if (!homography.empty() && testInnerLocal.isNotEmpty()) {
            val srcPts = MatOfPoint2f(*testInnerLocal.toTypedArray())
            val dstPts = MatOfPoint2f()
            Core.perspectiveTransform(srcPts, dstPts, homography)
            testInner.addAll(dstPts.toList())
        } else {
            testInner.addAll(testInnerLocal)
        }
        
        val innerReferenceCandidatesCount = refInner.size
        val innerLensCandidatesCount = testInner.size

        // 3. Match Inner Dots (One-To-One Mutual NN) with full telemetry
        var mutualNearestNeighborMatches = 0
        var rejectedByDistance = 0
        var rejectedByTopology = 0
        var rejectedByDuplicateAssignment = 0
        var rejectedByQuadrantRoi = 0
        var rejectedByGeometricConsistency = 0

        val validMatches = mutableListOf<Pair<Point, Point>>()
        val acceptedMatchesList = mutableListOf<MatchPairData>()
        val rejectedLensPoints = mutableListOf<PointCoord>()

        for (tp in testInner) {
            var bestD = 30.0; var bestRp: Point? = null
            for (rp in refInner) {
                val d = hypot(tp.x - rp.x, tp.y - rp.y)
                if (d < bestD) { bestD = d; bestRp = rp }
            }
            if (bestRp == null || bestD > 30.0) {
                rejectedByDistance++
                rejectedLensPoints.add(PointCoord(tp.x, tp.y))
                val dVal = bestD
                acceptedMatchesList.add(MatchPairData(bestRp?.x ?: tp.x, bestRp?.y ?: tp.y, tp.x, tp.y, false, if (bestRp == null) "No nearby reference dot" else "Distance ${String.format("%.1f", dVal)} > 30px"))
            } else {
                var bestD2 = 30.0; var bestTp: Point? = null
                for (tp2 in testInner) {
                    val d2 = hypot(tp2.x - bestRp.x, tp2.y - bestRp.y)
                    if (d2 < bestD2) { bestD2 = d2; bestTp = tp2 }
                }
                if (bestTp != tp) {
                    rejectedByDuplicateAssignment++
                    rejectedLensPoints.add(PointCoord(tp.x, tp.y))
                    acceptedMatchesList.add(MatchPairData(bestRp.x, bestRp.y, tp.x, tp.y, false, "Duplicate assignment / MNN failed"))
                } else {
                    mutualNearestNeighborMatches++
                    validMatches.add(Pair(bestRp, tp))
                    acceptedMatchesList.add(MatchPairData(bestRp.x, bestRp.y, tp.x, tp.y, true, null))
                }
            }
        }
        
        val trackedCount = validMatches.size

        val referenceInnerPoints = refInner.map { PointCoord(it.x, it.y) }
        val lensInnerPoints = testInner.map { PointCoord(it.x, it.y) }

        val matchedRefPoints = validMatches.map { it.first }.toSet()
        val rejectedReferencePoints = mutableListOf<PointCoord>()
        for (rp in refInner) {
            if (!matchedRefPoints.contains(rp)) {
                rejectedReferencePoints.add(PointCoord(rp.x, rp.y))
            }
        }

        var q1 = 0; var q2 = 0; var q3 = 0; var q4 = 0
        for (m in validMatches) {
            val t = m.second
            if (t.x >= cx && t.y < cy) q1++
            else if (t.x < cx && t.y < cy) q2++
            else if (t.x < cx && t.y >= cy) q3++
            else q4++
        }

        val coverage = (trackedCount.toDouble() / max(1.0, refInner.size.toDouble()) * 100).toInt()

        var qualityPass = true
        var qualityReason: String? = null
        if (trackedCount < 3 || (inliersCount ?: 0) < 20 || trackedCount < 30) {
            qualityPass = false
            qualityReason = if (trackedCount < 3) "Insufficient spatial correspondence (Matches: $trackedCount)"
                            else if ((inliersCount ?: 0) < 20) "Low RANSAC inliers ($inliersCount)"
                            else "Low tracked points ($trackedCount)"
        }

        val vectors = mutableListOf<DisplacementVector>()
        var meanDx = 0.0; var meanDy = 0.0
        
        val pointsX = mutableListOf<LocalPoint>()
        val disps = mutableListOf<LocalPoint>()
        
        for (m in validMatches) {
            val r = m.first; val t = m.second
            vectors.add(DisplacementVector(r.x, r.y, t.x, t.y))
            meanDx += (t.x - r.x); meanDy += (t.y - r.y)
            pointsX.add(LocalPoint(r.x - cx, r.y - cy))
            disps.add(LocalPoint(t.x - r.x, t.y - r.y))
        }
        if (trackedCount > 0) { meanDx /= trackedCount; meanDy /= trackedCount }
        
        var L1: Double? = null; var L2: Double? = null; var theta1: Double? = null; var theta2: Double? = null
        var optCx: Double? = null; var optCy: Double? = null
        
        if (trackedCount >= 3) {
            val fieldAffine = computeAffine(pointsX, disps)
            if (fieldAffine != null) {
                val A = fieldAffine[0]; val B = fieldAffine[1]; val C = fieldAffine[2]
                val D = fieldAffine[3]; val E = fieldAffine[4]; val F = fieldAffine[5]
                
                val detA = A * E - B * D
                if (abs(detA) > 1e-8) {
                    val X_oc = (B * F - C * E) / detA
                    val Y_oc = (C * D - A * F) / detA
                    optCx = cx + X_oc
                    optCy = cy + Y_oc
                }
                
                val Sxy = (B + D) / 2.0
                val tr = A + E
                val detS = A * E - Sxy * Sxy
                val root = sqrt(max(0.0, tr * tr / 4.0 - detS))
                val l1 = tr / 2.0 + root
                val l2 = tr / 2.0 - root
                L1 = l1; L2 = l2
                
                var t1 = atan2(l1 - A, Sxy) * 180.0 / PI
                if (t1 < 0) t1 += 180.0
                theta1 = t1
                theta2 = t1 + 90.0
            }
        }
        
        val confidence = if (trackedCount >= 100 && (registrationRms ?: 99.0) < 3.0) "HIGH" 
                         else if (trackedCount >= 50) "MEDIUM" 
                         else "LOW"

        val isotropic = if (L1 != null && L2 != null) (L1 + L2) / 2.0 else null
        val anisotropic = if (L1 != null && L2 != null) abs(L1 - L2) else null

        return LensMeasurementResult(
            analysisSuccess = true,
            analysisError = null,
            measurementQualityPass = qualityPass,
            qualityReason = qualityReason,
            sph = null, cyl = null, axis = null, calibrated = false,
            principal1 = L1, principal2 = L2, isotropic = isotropic, anisotropic = anisotropic,
            principalAngle1 = theta1, principalAngle2 = theta2,
            confidence = confidence,
            registrationRms = registrationRms,
            ransacInliers = inliersCount,
            trackedPoints = trackedCount,
            referencePoints = refInner.size,
            coverage = coverage,
            totalReferenceDots = totalReferenceDots,
            outerReferenceDotsCount = outerReferenceDotsCount,
            innerReferenceDotsCount = innerReferenceDotsCount,
            totalLensDots = totalLensDots,
            outerLensDotsCount = outerLensDotsCount,
            innerLensDotsCount = innerLensDotsCount,
            candidateOuterMatches = candidateOuterMatches,
            acceptedOuterMatches = acceptedOuterMatches,
            transformType = transformType,
            innerReferenceCandidatesCount = innerReferenceCandidatesCount,
            innerLensCandidatesCount = innerLensCandidatesCount,
            mutualNearestNeighborMatches = mutualNearestNeighborMatches,
            rejectedByDistance = rejectedByDistance,
            rejectedByTopology = rejectedByTopology,
            rejectedByDuplicateAssignment = rejectedByDuplicateAssignment,
            rejectedByQuadrantRoi = rejectedByQuadrantRoi,
            rejectedByGeometricConsistency = rejectedByGeometricConsistency,
            q1Matches = q1,
            q2Matches = q2,
            q3Matches = q3,
            q4Matches = q4,
            referenceInnerPoints = referenceInnerPoints,
            lensInnerPoints = lensInnerPoints,
            acceptedMatchesList = acceptedMatchesList,
            rejectedReferencePoints = rejectedReferencePoints,
            rejectedLensPoints = rejectedLensPoints,
            meanDx = meanDx.takeIf { trackedCount > 0 },
            meanDy = meanDy.takeIf { trackedCount > 0 },
            imageWidth = width,
            imageHeight = height,
            geometricCenterX = lensGeom.centerX,
            geometricCenterY = lensGeom.centerY,
            opticalCenterX = optCx,
            opticalCenterY = optCy,
            lensRadius = lensRadius,
            vectors = vectors
        )
    }

    private fun extractStableDots(frames: List<Bitmap>): List<Point> {
        val allDots = mutableListOf<List<Point>>()
        for (frame in frames) {
            allDots.add(detectBlobs(frame))
        }
        
        val dotClusters = mutableListOf<MutableList<Point>>()
        for (frameDots in allDots) {
            for (dot in frameDots) {
                var found = false
                for (cluster in dotClusters) {
                    val center = cluster[0]
                    if (hypot(center.x - dot.x, center.y - dot.y) < 15.0) {
                        cluster.add(dot)
                        found = true
                        break
                    }
                }
                if (!found) {
                    dotClusters.add(mutableListOf(dot))
                }
            }
        }
        
        val stableDots = mutableListOf<Point>()
        val minSupport = frames.size / 2
        for (cluster in dotClusters) {
            if (cluster.size >= minSupport) {
                val xs = cluster.map { it.x }.sorted()
                val ys = cluster.map { it.y }.sorted()
                stableDots.add(Point(xs[xs.size / 2], ys[ys.size / 2]))
            }
        }
        return stableDots
    }

    private fun detectBlobs(bitmap: Bitmap): List<Point> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            gray, thresh, 255.0, 
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
            Imgproc.THRESH_BINARY_INV, 
            21, 10.0
        )
        
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val numLabels = Imgproc.connectedComponentsWithStats(thresh, labels, stats, centroids)
        
        val blobs = mutableListOf<Point>()
        for (i in 1 until numLabels) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0]
            if (area in 10.0..800.0) {
                val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0]
                val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0]
                val aspect = w / h
                if (aspect in 0.3..3.3) {
                    val cx = centroids.get(i, 0)[0]
                    val cy = centroids.get(i, 1)[0]
                    blobs.add(Point(cx, cy))
                }
            }
        }
        
        mat.release(); gray.release(); thresh.release(); labels.release(); stats.release(); centroids.release()
        return blobs
    }

    class LocalPoint(val x: Double, val y: Double)

    fun computeAffine(src: List<LocalPoint>, dst: List<LocalPoint>): DoubleArray? {
        if (src.size < 3) return null
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        var su = 0.0; var sux = 0.0; var suy = 0.0
        var sv = 0.0; var svx = 0.0; var svy = 0.0
        val n = src.size.toDouble()
        
        for (i in src.indices) {
            val x = src[i].x; val y = src[i].y
            val u = dst[i].x; val v = dst[i].y
            
            sx += x; sy += y
            sxx += x*x; syy += y*y; sxy += x*y
            su += u; sux += u*x; suy += u*y
            sv += v; svx += v*x; svy += v*y
        }
        
        val det = sxx*(syy*n - sy*sy) - sxy*(sxy*n - sx*sy) + sx*(sxy*sy - sx*syy)
        if (abs(det) < 1e-10) return null
        
        val inv00 = (syy*n - sy*sy) / det
        val inv01 = (sx*sy - sxy*n) / det
        val inv02 = (sxy*sy - sx*syy) / det
        val inv11 = (sxx*n - sx*sx) / det
        val inv12 = (sx*sxy - sxx*sy) / det
        val inv22 = (sxx*syy - sxy*sxy) / det
        
        val a = inv00*sux + inv01*suy + inv02*su
        val b = inv01*sux + inv11*suy + inv12*su
        val c = inv02*sux + inv12*suy + inv22*su
        
        val d = inv00*svx + inv01*svy + inv02*sv
        val e = inv01*svx + inv11*svy + inv12*sv
        val f = inv02*svx + inv12*svy + inv22*sv
        
        return doubleArrayOf(a, b, c, d, e, f)
    }

}
