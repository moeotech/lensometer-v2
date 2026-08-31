import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

# Remove retryCountForCurrentRun
content = re.sub(r'var retryCountForCurrentRun by remember \{ mutableStateOf\(0\) \}\n\s+', '', content)
content = re.sub(r'Text\("Attempt \$\{retryCountForCurrentRun \+ 1\} of 3", color = Color\.LightGray\)\n\s+', '', content)
content = content.replace("retryCountForCurrentRun = 0\n", "")

# Fix the if (false) block
pattern = r"if \(false\) \{\n\s*\} else \{\n\s*analysisErrorMessage = \"\"\n\s*runResults\[currentRunIndex\] = result\n\s*if \(currentRunIndex < 2\) \{"
replacement = """analysisErrorMessage = ""
                                runResults[currentRunIndex] = result
                                
                                if (currentRunIndex < 2) {"""
content = content.replace(pattern, replacement)

# Wait, the original code had:
#                             analysisErrorMessage = if (!result.success) "Run failed: ${result.errorMessage}" else ""
#                             runResults[currentRunIndex] = result
#                             retryCountForCurrentRun = 0
#                             
#                             if (false) {
#                             } else {
#                                 analysisErrorMessage = ""
#                                 runResults[currentRunIndex] = result
#                                 retryCountForCurrentRun = 0
#                                 
#                                 if (currentRunIndex < 2) {

# Let's just do a string replacement for that whole block

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
