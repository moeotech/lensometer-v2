import sys

filepath = 'app/src/main/java/com/example/ui/V4ExperimentScreen.kt'
with open(filepath, 'r') as f:
    content = f.read()

target = '                Text("Lens ROI: ${tel.lensRoiSource} (${String.format("${String.format("${String.format("%.1f", tel.lensRoiInnerRadius)}", tel.lensRoiCenterY)}", tel.lensRoiCenterX)}, %.1f) R=%.1f", color = Color.Yellow)'
replacement = """                Text(
                    "Lens ROI: ${tel.lensRoiSource} " +
                    "(${String.format("%.1f", tel.lensRoiCenterX)}, " +
                    "${String.format("%.1f", tel.lensRoiCenterY)}) " +
                    "Rin=${String.format("%.1f", tel.lensRoiInnerRadius)} " +
                    "Rout=${String.format("%.1f", tel.lensRoiOuterRadius)}",
                    color = Color.Yellow
                )"""

if target in content:
    content = content.replace(target, replacement)
    with open(filepath, 'w') as f:
        f.write(content)
    print("Replaced successfully.")
else:
    print("Target string not found!")
    sys.exit(1)
