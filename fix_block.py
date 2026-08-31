import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

pattern = """                            analysisErrorMessage = if (!result.success) "Run failed: ${result.errorMessage}" else ""
                            runResults[currentRunIndex] = result
                                                           
                            if (false) {
                            } else {
                                analysisErrorMessage = ""
                                runResults[currentRunIndex] = result
                                                                   
                                if (currentRunIndex < 2) {"""

replacement = """                            analysisErrorMessage = if (!result.success) "Run failed: ${result.errorMessage}" else ""
                            runResults[currentRunIndex] = result
                                                           
                            if (currentRunIndex < 2) {"""

content = content.replace(pattern, replacement)
# There is an extra closing brace because of `} else {` being removed. Wait, let me just parse and fix it.
