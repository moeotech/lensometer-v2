import os
import re

path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

target = """                Text("Matched dots: ${result.trackedDots}", color = Color.LightGray)"""

replacement = """                Text("Matched dots: ${result.trackedDots}", color = Color.LightGray)
                Text("Common Grid Points: ${result.commonGridPointsAcrossRuns}", color = Color.LightGray)
                Text("Correspondence Consistency: ${String.format(\"%.1f\", result.correspondenceConsistency * 100.0)}%", color = Color.LightGray)
                Text("Center StdPx: ${String.format(\"%.2f\", result.centerStdPx)}", color = Color.LightGray)
                Text("Tensor Std: ${String.format(\"%.6f\", result.tensorStd)}", color = Color.LightGray)"""

text = text.replace(target, replacement)

target2 = """                    Text("OPTICAL FIELD", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)"""

replacement2 = """                    Text("TOPOLOGY", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Input: ${run.matchRejections[\"topologyInputDots\"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Assigned: ${run.matchRejections[\"topologyAssignedDots\"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Unassigned: ${run.matchRejections[\"topologyUnassignedDots\"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Collisions: ${run.matchRejections[\"topologyCollisions\"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Consistency errs: ${run.matchRejections[\"topologyConsistencyErrors\"] ?: 0}", color = Color.LightGray, fontSize = 12.sp)

                    Text("OPTICAL CENTER", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Valid: ${run.opticalCenterValid}", color = if (run.opticalCenterValid) Color.Green else Color.Red, fontSize = 12.sp)
                    Text("Cond num: ${String.format(\"%.2f\", run.opticalCenterConditionNumber)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Confidence: ${String.format(\"%.3f\", run.opticalCenterConfidence)}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Center: (${String.format(\"%.1f\", run.opticalCenterX)}, ${String.format(\"%.1f\", run.opticalCenterY)})", color = Color.LightGray, fontSize = 12.sp)

                    Text("OPTICAL FIELD", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)"""

text = text.replace(target2, replacement2)

with open(path, "w") as f:
    f.write(text)
