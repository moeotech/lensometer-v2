with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "analysisErrorMessage = if (!result.success)" in line:
        new_lines.append(line)
        new_lines.append("                            runResults[currentRunIndex] = result\n")
        new_lines.append("                            if (currentRunIndex < 2) {\n")
        skip = True
        continue
    
    if skip:
        if "if (currentRunIndex < 2) {" in line:
            skip = False
            continue
        elif "} else {" in line or "if (false) {" in line or "analysisErrorMessage = \"\"" in line or "runResults[currentRunIndex] = result" in line:
            continue
    
    # We also need to remove one closing brace for the `if (false)`
    # Wait, the structure was:
    # if (false) {
    # } else {
    #    ...
    #    if (currentRunIndex < 2) { ... } else { ... }
    # }
    
    new_lines.append(line)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.writelines(new_lines)
