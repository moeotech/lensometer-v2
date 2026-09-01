import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

# UI Text
old_ui = 'Text("Inside Lens Cells: Ref=${tel.insideLensRefCells}, Lens=${tel.insideLensLensCells}, Common=${tel.insideLensCommonCells}", color = Color.LightGray)'
new_ui = 'Text("Lens ROI: ${tel.lensRoiSource} (%.1f, %.1f) R=%.1f", color = Color.Yellow)\n                Text("Inside Lens Cells: Ref=${tel.insideLensRefCells}, Lens=${tel.insideLensLensCells}, Common=${tel.insideLensCommonCells}", color = Color.LightGray)'
new_ui = new_ui.replace('%.1f', '${String.format("%.1f", tel.lensRoiCenterX)}', 1)
new_ui = new_ui.replace('%.1f', '${String.format("%.1f", tel.lensRoiCenterY)}', 1)
new_ui = new_ui.replace('%.1f', '${String.format("%.1f", tel.lensRoiInnerRadius)}', 1)

content = content.replace(old_ui, new_ui)

# Export logs
old_export = 'appendLine("Global Tx: ${tel.globalTx}")'
new_export = '''appendLine("Lens ROI Source: ${tel.lensRoiSource}")
                        appendLine("Lens ROI Center X: ${tel.lensRoiCenterX}")
                        appendLine("Lens ROI Center Y: ${tel.lensRoiCenterY}")
                        appendLine("Lens ROI Inner Radius: ${tel.lensRoiInnerRadius}")
                        appendLine("Lens ROI Outer Radius: ${tel.lensRoiOuterRadius}")
                        appendLine("Global Tx: ${tel.globalTx}")'''

content = content.replace(old_export, new_export)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
