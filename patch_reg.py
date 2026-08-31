import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """    private fun estimateSimilarityTransform(srcPts: List<Point>, dstPts: List<Point>, ransacThresh: Double): Pair<Mat, Mat> {
        val srcMat = MatOfPoint2f().apply { fromList(srcPts) }
        val dstMat = MatOfPoint2f().apply { fromList(dstPts) }
        val mask = Mat()
        val partial = Calib3d.estimateAffinePartial2D(srcMat, dstMat, mask, Calib3d.RANSAC, ransacThresh)
        return Pair(partial, mask)
    }"""

import re
text = re.sub(r'    private fun estimateStrictRigid.*?    }\n', replacement + "\n", text, flags=re.DOTALL)

# Now, update calls from estimateStrictRigid to estimateSimilarityTransform
text = text.replace("estimateStrictRigid(", "estimateSimilarityTransform(")
with open(path, "w") as f:
    f.write(text)
