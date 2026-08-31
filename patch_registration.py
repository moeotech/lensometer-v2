import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
            val useRigidFallback = anchorRef.size < 4
            
            val regSrc: List<Point>
            val regDst: List<Point>
            var modelName = "Affine_OuterAnchors"
            var fallbackTriggered = false
            if (useRigidFallback) {
                regSrc = matchedLens
                regDst = matchedRef
                modelName = "RIGID_FALLBACK_UNTRUSTED"
                fallbackTriggered = true
            } else {
                regSrc = anchorLens
                regDst = anchorRef
            }
"""

start_str = """
            val useRigidFallback = anchorRef.size < 4
            
            val regSrc: List<Point>
            val regDst: List<Point>
            if (useRigidFallback) {
                regSrc = matchedLens
                regDst = matchedRef
            } else {
                regSrc = anchorLens
                regDst = anchorRef
            }
"""

text = text.replace(start_str, replacement)

# We also need to fail measurement quality if fallback is triggered
# at the end of analyzePoints:
end_rep = """
            return V4RunResult(
                success = true,
                measurementQualityPass = !fallbackTriggered,
                qualityMessage = if (fallbackTriggered) "REGISTRATION INSUFFICIENT ANCHORS (Outer < 4)" else "",
                axis = axisDeg,
                lambda1 = l1,
                lambda2 = l2,
                isotropic = iso,
                anisotropic = aniso,
                trackedDots = matchedRef.size,
                registrationFeatureCount = regSrc.size,
                registrationInliers = inliersCount,
                registrationRms = registrationRms,
                opticalFieldInputCount = matchedRef.size,
                opticalFieldRetainedCount = finalRef.size,
                fieldFitRms = fitRms,
                referencePoints = matchedRef,
                observedPoints = matchedLens,
                refWidth = w.toInt(),
                refHeight = h.toInt(),
                registrationModel = modelName,
"""
# find the return in analyzePoints
# "            return V4RunResult(\n                success = true,\n                axis = axisDeg,"
idx = text.find("            return V4RunResult(\n                success = true,\n                axis = axisDeg,")
if idx != -1:
    end_orig = text[idx : text.find("registrationModel = \"Affine_OuterAnchors\",", idx) + len("registrationModel = \"Affine_OuterAnchors\",")]
    text = text.replace(end_orig, end_rep)
else:
    # try another format
    idx = text.find("            return V4RunResult(\n                success = true,\n")
    if idx != -1:
        end_orig = text[idx : text.find("registrationModel = ", idx) + 60]
        # just do it via regex
        import re
        text = re.sub(r'return V4RunResult\(\s*success = true,\s*axis = axisDeg,.*registrationModel = "[^"]*",', end_rep.strip(), text, flags=re.DOTALL)


with open(path, "w") as f:
    f.write(text)
