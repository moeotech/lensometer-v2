import os
import re

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """
            val r00 = transformMat.get(0, 0)[0]
            val r10 = transformMat.get(1, 0)[0]
            val registrationScale = Math.hypot(r00, r10)
            val registrationRotationDeg = atan2(r10, r00) * 180.0 / Math.PI
            val registrationTx = transformMat.get(0, 2)[0]
            val registrationTy = transformMat.get(1, 2)[0]
            val registrationModel = if (useRigidFallback) "SIMILARITY_FALLBACK" else "SIMILARITY_ANCHOR"
"""

target = """
            val r00 = transformMat.get(0, 0)[0]
            val r10 = transformMat.get(1, 0)[0]
            val registrationRotationDeg = atan2(r10, r00) * 180.0 / Math.PI
            val registrationTx = transformMat.get(0, 2)[0]
            val registrationTy = transformMat.get(1, 2)[0]
            val registrationModel = if (useRigidFallback) "RIGID_FALLBACK" else "RIGID_ANCHOR"
"""

text = text.replace(target, replacement)

# replace modelName variables
text = text.replace('modelName = "RIGID_FALLBACK_UNTRUSTED"', 'modelName = "SIMILARITY_FALLBACK_UNTRUSTED"')

# Now, we also need to update the end of analyzePoints where it constructs V4RunResult.
# Wait, V4RunResult might already have registrationScale? Let's check `registrationScale = 1.0,`
text = text.replace("registrationScale = 1.0,", "registrationScale = registrationScale,")

with open(path, "w") as f:
    f.write(text)
