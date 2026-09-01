import re

with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'r') as f:
    content = f.read()

# Add a helper method
helper = """
    fun validateRoi(rawCx: Double, rawCy: Double, rawW: Double, rawH: Double, imgW: Int, imgH: Int): Pair<Boolean, String> {
        val minDim = min(imgW, imgH)
        val minR = min(rawW, rawH) / 2.0
        val maxR = max(rawW, rawH) / 2.0
        val aspect = if (minR > 0) maxR / minR else Double.MAX_VALUE
        
        if (rawCx < 0.0 || rawCy < 0.0 || rawCx >= imgW || rawCy >= imgH) {
            return Pair(true, "Center outside image")
        } else if (maxR * 1.2 > 0.48 * minDim) {
            return Pair(true, "Radius too large")
        } else if (minR * 0.8 < 0.1 * minDim) {
            return Pair(true, "Radius too small")
        } else if (aspect > 2.0) {
            return Pair(true, "Extreme aspect ratio")
        } else if (rawCx - maxR < -0.2 * imgW || rawCx + maxR > imgW * 1.2 || rawCy - maxR < -0.2 * imgH || rawCy + maxR > imgH * 1.2) {
            return Pair(true, "Ellipse out of bounds")
        }
        return Pair(false, "")
    }
"""

# Replace inside analyze:
analyze_target = """                // 1. Never accept AUTO ellipse if center is outside image
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
                }"""

analyze_repl = """                val validation = validateRoi(rawCx, rawCy, rawW, rawH, imgW, imgH)
                autoRoiRejected = validation.first
                autoRoiRejectReason = validation.second
                
                if (!autoRoiRejected) {
                    roiCenterX = rawCx
                    roiCenterY = rawCy
                    roiInnerR = minR * 0.8
                    roiOuterR = maxR * 1.2
                    roiSource = "AUTO"
                }"""

content = content.replace(analyze_target, analyze_repl)
content = content.replace('object V6StructuredDeflectometryAnalyzer {', 'object V6StructuredDeflectometryAnalyzer {\n' + helper)

with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'w') as f:
    f.write(content)
print("Extracted successfully.")
