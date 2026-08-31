import os
path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "var q1 = 0; var q2 = 0; var q3 = 0; var q4 = 0" in line:
        if skip:
            # We skip the second one
            pass
        else:
            new_lines.append(line)
            skip = True
    else:
        new_lines.append(line)

with open(path, "w") as f:
    f.writelines(new_lines)
