import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

# Replace UI lines
content = re.sub(
    r'Text\("Reference Assigned Cells: \$\{tel.referenceAssignedCells\} \| Lens Assigned Cells: \$\{tel.lensAssignedCells\}", color = Color.LightGray\)',
    'Text("Inside Lens Cells: Ref=${tel.insideLensRefCells}, Lens=${tel.insideLensLensCells}, Common=${tel.insideLensCommonCells}", color = Color.LightGray)',
    content
)

content = re.sub(
    r'Text\("Valid Directional Vectors: \$\{tel.validDirectionalVectors\}", color = Color.Green, fontWeight = FontWeight.Bold\)',
    'Text("Accepted Vectors: ${tel.acceptedVectorCount} / ${tel.rawVectorCount}", color = Color.Green, fontWeight = FontWeight.Bold)',
    content
)

content = re.sub(
    r'Text\("Ratio Median: \$\{String\.format\("%.4f", tel\.ratioMedian\)\} \| Ratio Range: \$\{String\.format\("%.4f", tel\.ratioRange\)\}", color = Color\.Cyan\)',
    'Text("Ratio Median: ${String.format("%.4f", tel.acceptedRatioMedian)} | MAD: ${String.format("%.4f", tel.acceptedRatioMAD)}", color = Color.Cyan)',
    content
)

content = re.sub(
    r'Text\("Directional Consistency: \$\{tel\.directionalConsistency\}", color = Color\.LightGray\)',
    'Text("Directional Consistency: ${tel.directionalConsistency} (${tel.directionalConsistencyReason})", color = Color.LightGray)',
    content
)

content = re.sub(
    r'Text\("Astigmatic Amplitude: \$\{String\.format\("%.4f", f\.astigmaticAmplitude\)\}", color = Color\.LightGray\)',
    'Text("Amplitude: ${String.format("%.4f", f.astigmaticAmplitude)} | Coverage: ${String.format("%.1f%%", f.angularCoverage)}", color = Color.LightGray)',
    content
)

content = re.sub(
    r'Text\("Principal Orientation: \$\{String\.format\("%.1f°", f\.principalOrientation\)\}", color = Color\.Cyan\)',
    'Text("RAW PRINCIPAL ORIENTATION: ${String.format("%.1f°", f.principalOrientation)}", color = Color.Cyan)\\n                    Text("Fit RMS: ${String.format("%.4f", f.fitRms)} | Fit MAD: ${String.format("%.4f", f.fitMad)}", color = Color.LightGray)',
    content
)

# Export strings
export_old = r'''                        appendLine\("Reference Assigned Cells: \$\{tel\.referenceAssignedCells\}"\)
                        appendLine\("Lens Assigned Cells: \$\{tel\.lensAssignedCells\}"\)
                        appendLine\("Valid Directional Vectors: \$\{tel\.validDirectionalVectors\}"\)
                        appendLine\("Ratio Median: \$\{tel\.ratioMedian\}"\)
                        appendLine\("Ratio Range: \$\{tel\.ratioRange\}"\)
                        appendLine\("Directional Consistency: \$\{tel\.directionalConsistency\}"\)'''

export_new = r'''                        appendLine("Global Tx: ${tel.globalTx}")
                        appendLine("Global Ty: ${tel.globalTy}")
                        appendLine("Global Rotation: ${tel.globalRotation}")
                        appendLine("Global Scale: ${tel.globalScale}")
                        appendLine("Inside Lens Ref Cells: ${tel.insideLensRefCells}")
                        appendLine("Inside Lens Lens Cells: ${tel.insideLensLensCells}")
                        appendLine("Inside Lens Common Cells: ${tel.insideLensCommonCells}")
                        appendLine("Outside Lens Reg Cells: ${tel.outsideLensRegistrationCells}")
                        appendLine("Raw Vector Count: ${tel.rawVectorCount}")
                        appendLine("Accepted Vector Count: ${tel.acceptedVectorCount}")
                        appendLine("Rejected Ratio Outlier: ${tel.rejectedRatioOutlier}")
                        appendLine("Rejected Grid Mismatch: ${tel.rejectedGridMismatch}")
                        appendLine("Rejected Boundary: ${tel.rejectedBoundary}")
                        appendLine("Rejected Spatial Consistency: ${tel.rejectedSpatialConsistency}")
                        appendLine("Raw Ratio Median: ${tel.rawRatioMedian}")
                        appendLine("Accepted Ratio Median: ${tel.acceptedRatioMedian}")
                        appendLine("Accepted Ratio MAD: ${tel.acceptedRatioMAD}")
                        appendLine("Accepted Ratio P05: ${tel.acceptedRatioP05}")
                        appendLine("Accepted Ratio P95: ${tel.acceptedRatioP95}")
                        appendLine("Directional Consistency: ${tel.directionalConsistency}")
                        appendLine("Directional Consistency Reason: ${tel.directionalConsistencyReason}")'''
content = re.sub(export_old, export_new, content)

fit_old = r'''                            appendLine\("Orientation: \$\{f\.principalOrientation\}"\)'''
fit_new = r'''                            appendLine("RAW PRINCIPAL ORIENTATION: ${f.principalOrientation}")
                            appendLine("Fit RMS: ${f.fitRms}")
                            appendLine("Fit MAD: ${f.fitMad}")
                            appendLine("Angular Coverage: ${f.angularCoverage}")
                            appendLine("Sample Count: ${f.sampleCount}")'''
content = re.sub(fit_old, fit_new, content)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
