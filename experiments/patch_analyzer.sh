cat << 'INNER_EOF' > app/src/main/java/com/example/analysis/LensAnalyzer.kt
package com.example.analysis

import android.graphics.Bitmap
import com.example.model.LensMeasurementResult
import com.example.model.DisplacementVector
import com.example.model.LensGeometry
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
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return emptyResult("LOW - Missing Frames")
        }
        if (lensGeom == null) {
            return emptyResult("LOW - Missing Lens Geometry")
        }

        val width = noLensFrames[0].width
        val height = noLensFrames[0].height
        val cx = width / 2.0
        val cy = height / 2.0
        val lensRadius = max(lensGeom.width, lensGeom.height) / 2.0
        
        // 1. Multi-frame averaging / median of dots
        val refDots = extractStableDots(noLensFrames)
        val testDotsLocal = extractStableDots(withLensFrames)
        
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
        
        val testInnerLocal = mutableListOf<Point>(); val testOuterLocal = mutableListOf<Point>()
        for (p in testDotsLocal) {
            val rSq = getNormalizedRadiusSq(p)
            if (rSq < 0.81) testInnerLocal.add(p)
            else if (rSq > 1.21) testOuterLocal.add(p)
        }
        
        // 2. Global Registration (using REAL OpenCV Homography)
        var registrationRms = 0.0
        var inliersCount = 0
        var homography = Mat.eye(3, 3, CvType.CV_64F)
        
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
                    var bestD2 = 30.0; var bestTp: Point? = null
                    for (tp2 in testOuterLocal) {
                        val d2 = hypot(tp2.x - bestRp.x, tp2.y - bestRp.y)
                        if (d2 < bestD2) { bestD2 = d2; bestTp = tp2 }
                    }
                    if (bestTp == tp) {
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
                    inliersCount = Core.countNonZero(mask)
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
                    registrationRms = if (inliersCount > 0) sqrt(sqErr / inliersCount) else 0.0
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
        
        // 3. Match Inner Dots (One-To-One Mutual NN)
        val validMatches = mutableListOf<Pair<Point, Point>>()
        for (tp in testInner) {
            var bestD = 30.0; var bestRp: Point? = null
            for (rp in refInner) {
                val d = hypot(tp.x - rp.x, tp.y - rp.y)
                if (d < bestD) { bestD = d; bestRp = rp }
            }
            if (bestRp != null) {
                var bestD2 = 30.0; var bestTp: Point? = null
                for (tp2 in testInner) {
                    val d2 = hypot(tp2.x - bestRp.x, tp2.y - bestRp.y)
                    if (d2 < bestD2) { bestD2 = d2; bestTp = tp2 }
                }
                if (bestTp == tp) {
                    validMatches.add(Pair(bestRp, tp))
                }
            }
        }
        
        val trackedCount = validMatches.size
        
        // Quality Gates
        if (homography.empty() || inliersCount < 20 || registrationRms.isNaN() || registrationRms.isInfinite() || trackedCount < 30) {
            val reason = if (homography.empty()) "Homography failed"
                         else if (inliersCount < 20) "Low RANSAC inliers"
                         else if (registrationRms.isNaN() || registrationRms.isInfinite()) "Invalid RMS"
                         else "Low tracked points"
            return emptyResult("FAILED - \$reason")
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
        
        var L1 = 0.0; var L2 = 0.0; var theta1 = 0.0; var theta2 = 0.0
        var optCx = cx; var optCy = cy
        
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
            L1 = tr / 2.0 + root
            L2 = tr / 2.0 - root
            
            theta1 = atan2(L1 - A, Sxy) * 180.0 / PI
            var t1Deg = theta1
            if (t1Deg < 0) t1Deg += 180.0
            theta1 = t1Deg
            theta2 = t1Deg + 90.0
        }
        
        val confidence = if (trackedCount >= 100 && registrationRms < 3.0) "HIGH" 
                         else if (trackedCount >= 50) "MEDIUM" 
                         else "LOW"
        val coverage = (trackedCount.toDouble() / max(1.0, refInner.size.toDouble()) * 100).toInt()
        
        return LensMeasurementResult(
            sph = 0.0, cyl = 0.0, axis = 0.0, calibrated = false,
            confidence = confidence,
            trackedPoints = trackedCount,
            coverage = coverage,
            meanDx = meanDx, meanDy = meanDy,
            p1 = L1, p1Angle = theta1,
            p2 = L2, p2Angle = theta2,
            registrationRms = registrationRms,
            ransacInliers = inliersCount,
            imageWidth = width, imageHeight = height,
            geometricCenterX = lensGeom.centerX, geometricCenterY = lensGeom.centerY,
            opticalCenterX = optCx, opticalCenterY = optCy,
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

    // Make computeAffine public for testing
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

    private fun emptyResult(confidence: String): LensMeasurementResult {
        return LensMeasurementResult(0.0, 0.0, 0.0, false, confidence, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList())
    }
}
INNER_EOF
bash patch_analyzer.sh