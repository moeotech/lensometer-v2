import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "if (inlierSrc.size < 2) return Pair(Mat(), mask)" in line and "var cxSrc = 0.0" in lines[i+1]:
        skip = True
    
    if skip:
        # Check if we hit the end of the garbage. The end is the start of the next function.
        if "suspend fun analyze(" in line or "private fun" in line or "fun " in line and i > 250:
            skip = False
        else:
            continue
            
    if not skip:
        # Wait, the closing brace of estimateSimilarityTransform is already there, but followed by another `    }`
        # I see `        }\n` right before `        if (inlierSrc.size < 2)` in the output!
        # Let's just manually delete lines from exactly the `        }` after `    }` up to `fun analyze(`
        pass
