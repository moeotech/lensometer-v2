import os
path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """            if (result.success) {
                if (result.measurementQualityPass) {
                    Text("MEASUREMENT QUALITY: PASS", color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("MEASUREMENT QUALITY: FAIL", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("REASON: ${result.qualityMessage}", color = Color.Yellow)
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("SPH: ${result.sphDisplay}", color = Color.Cyan, fontSize = 24.sp)"""

text = text.replace("""            if (result.success) {
                Text("SPH: ${result.sphDisplay}", color = Color.Cyan, fontSize = 24.sp)""", replacement)

with open(path, "w") as f:
    f.write(text)
