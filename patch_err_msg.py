import os
import re

path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

# Remove from ANALYZING
text = text.replace("""                V4Step.ANALYZING -> {
                    Text("ANALYZING...", color = Color.Cyan)
                    if (analysisErrorMessage.isNotEmpty()) {
                        Text(analysisErrorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    LaunchedEffect(Unit) {""", """                V4Step.ANALYZING -> {
                    Text("ANALYZING...", color = Color.Cyan)
                    LaunchedEffect(Unit) {""")

# Add above `when (currentStep)`
text = text.replace("""            when (currentStep) {""", """            if (analysisErrorMessage.isNotEmpty()) {
                Text(analysisErrorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            when (currentStep) {""")

with open(path, "w") as f:
    f.write(text)
