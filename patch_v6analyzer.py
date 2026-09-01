import re

with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'r') as f:
    content = f.read()

# First, modify analyzePoints signature
sig_target = r"fun analyzePoints\(refPoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920, providedRoi: V6LensRoi\? = null, providedRoiSource: String = \"FALLBACK\"\): V6Result \{"
sig_repl = """fun analyzePoints(
        refPoints: List<Point>, 
        lensPoints: List<Point>, 
        width: Int = 1080, 
        height: Int = 1920, 
        providedRoi: V6LensRoi? = null, 
        providedRoiSource: String = "FALLBACK",
        autoRoiRejected: Boolean = false,
        autoRoiRejectReason: String = "",
        rawEllipseCenterX: Double = 0.0,
        rawEllipseCenterY: Double = 0.0,
        rawEllipseWidth: Double = 0.0,
        rawEllipseHeight: Double = 0.0
    ): V6Result {"""

content = re.sub(sig_target, sig_repl, content)

# Next, modify telemetry creation to include the new properties
telemetry_target = r"val lensRoiSource = providedRoiSource,\n\s*referenceAssignedCells"
telemetry_repl = """val lensRoiSource = providedRoiSource,
            autoRoiRejected = autoRoiRejected,
            autoRoiRejectReason = autoRoiRejectReason,
            rawEllipseCenterX = rawEllipseCenterX,
            rawEllipseCenterY = rawEllipseCenterY,
            rawEllipseWidth = rawEllipseWidth,
            rawEllipseHeight = rawEllipseHeight,
            referenceAssignedCells"""
content = re.sub(telemetry_target, telemetry_repl, content)

# Next, modify the analyze function to include validation and fallback logic
analyze_target = r"""        var roiSource = "FALLBACK"
        var roiCenterX = noLensFrames\[0\]\.width / 2\.0
        var roiCenterY = noLensFrames\[0\]\.height / 2\.0
        var roiInnerR = min\(noLensFrames\[0\]\.width, noLensFrames\[0\]\.height\) \* 0\.25
        var roiOuterR = min\(noLensFrames\[0\]\.width, noLensFrames\[0\]\.height\) \* 0\.40

        for \(frame in withLensFrames\) \{
            val ell = com\.example\.ui\.detectLensEllipse\(frame\)
            if \(ell != null\) \{
                roiCenterX = ell\.center\.x
                roiCenterY = ell\.center\.y
                val minR = min\(ell\.size\.width, ell\.size\.height\) / 2\.0
                val maxR = max\(ell\.size\.width, ell\.size\.height\) / 2\.0
                roiInnerR = minR \* 0\.8
                roiOuterR = maxR \* 1\.2
                roiSource = "AUTO"
                break
            \}
        \}
        
        val providedRoi = V6LensRoi\(roiCenterX, roiCenterY, roiInnerR, roiOuterR\)
        return@withContext analyzePoints\(refPoints, lensPoints, noLensFrames\[0\]\.width, noLensFrames\[0\]\.height, providedRoi, roiSource\)"""

analyze_repl = """        var roiSource = "FALLBACK"
        val imgW = noLensFrames[0].width
        val imgH = noLensFrames[0].height
        val minDim = min(imgW, imgH)
        var roiCenterX = imgW / 2.0
        var roiCenterY = imgH / 2.0
        var roiInnerR = minDim * 0.25
        var roiOuterR = minDim * 0.40

        var autoRoiRejected = false
        var autoRoiRejectReason = ""
        var rawCx = 0.0
        var rawCy = 0.0
        var rawW = 0.0
        var rawH = 0.0

        for (frame in withLensFrames) {
            val ell = com.example.ui.detectLensEllipse(frame)
            if (ell != null) {
                rawCx = ell.center.x
                rawCy = ell.center.y
                rawW = ell.size.width
                rawH = ell.size.height
                
                val minR = min(rawW, rawH) / 2.0
                val maxR = max(rawW, rawH) / 2.0
                val aspect = if (minR > 0) maxR / minR else Double.MAX_VALUE
                
                // 1. Never accept AUTO ellipse if center is outside image
                if (rawCx < 0.0 || rawCy < 0.0 || rawCx >= imgW || rawCy >= imgH) {
                    autoRoiRejected = true
                    autoRoiRejectReason = "Center outside image"
                } 
                // 2. Reject if ellipse size/radius is physically implausible
                else if (maxR * 1.2 > 0.48 * minDim) {
                    autoRoiRejected = true
                    autoRoiRejectReason = "Radius too large"
                } else if (minR * 0.8 < 0.1 * minDim) {
                    autoRoiRejected = true
                    autoRoiRejectReason = "Radius too small"
                } else if (aspect > 2.0) {
                    autoRoiRejected = true
                    autoRoiRejectReason = "Extreme aspect ratio"
                } 
                // 3. Reject if too much of the ellipse lies outside the image
                else if (rawCx - maxR < -0.2 * imgW || rawCx + maxR > imgW * 1.2 || rawCy - maxR < -0.2 * imgH || rawCy + maxR > imgH * 1.2) {
                    autoRoiRejected = true
                    autoRoiRejectReason = "Ellipse out of bounds"
                } else {
                    roiCenterX = rawCx
                    roiCenterY = rawCy
                    roiInnerR = minR * 0.8
                    roiOuterR = maxR * 1.2
                    roiSource = "AUTO"
                    autoRoiRejected = false
                    autoRoiRejectReason = ""
                }
                break
            }
        }
        
        val providedRoi = V6LensRoi(roiCenterX, roiCenterY, roiInnerR, roiOuterR)
        var result = analyzePoints(refPoints, lensPoints, imgW, imgH, providedRoi, roiSource, autoRoiRejected, autoRoiRejectReason, rawCx, rawCy, rawW, rawH)
        
        // 4. After ROI generation, verify it produces some cells. Fall back if not.
        if (roiSource == "AUTO" && (result.telemetry.insideLensCommonCells == 0 || result.telemetry.outsideLensRegistrationCells == 0)) {
            val fallbackRoi = V6LensRoi(imgW / 2.0, imgH / 2.0, minDim * 0.25, minDim * 0.40)
            result = analyzePoints(refPoints, lensPoints, imgW, imgH, fallbackRoi, "FALLBACK", true, "Zero valid cells inside/outside", rawCx, rawCy, rawW, rawH)
        }
        
        return@withContext result"""

if 'val providedRoi = V6LensRoi(roiCenterX, roiCenterY, roiInnerR, roiOuterR)' in content:
    content = re.sub(analyze_target, analyze_repl, content)
    with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'w') as f:
        f.write(content)
    print("Analyzer patched successfully.")
else:
    print("Could not find analyze function body.")
