import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

# 1. Inject local context and clipboard manager
pattern_dialog_start = r"fun V4ResultDialog\(result: V4Result, onDismiss: \(\) -> Unit\) \{"
replacement_dialog_start = """fun V4ResultDialog(result: V4Result, onDismiss: () -> Unit) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current"""
content = content.replace(pattern_dialog_start, replacement_dialog_start)

# 2. Replace the empty onClick
pattern_button = r"Button\(onClick = \{\n\s*\}, modifier = Modifier\.fillMaxWidth\(\), colors = ButtonDefaults\.buttonColors\(containerColor = Color\.Magenta\)\) \{"

replacement_button = """Button(onClick = {
                val exportStr = buildString {
                    appendLine("--- V4 LENSOMETER EXPORT ---")
                    appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                    appendLine("Device: ${android.os.Build.MODEL}")
                    appendLine("Status: ${if (result.success) "SUCCESS" else "FAILED"}")
                    appendLine("Quality: ${if (result.measurementQualityPass) "PASS" else "FAIL"} (${result.qualityMessage})")
                    
                    val p = result.experimentalPower
                    appendLine("\\n--- EXPERIMENTAL POWER ---")
                    appendLine("SPH EXP: ${p?.sphere ?: "N/A"}")
                    appendLine("CYL EXP: ${p?.cylinder ?: "N/A"}")
                    appendLine("AXIS EXP: ${p?.axis ?: "N/A"}")
                    appendLine("Confidence: ${p?.confidence ?: "N/A"}")
                    
                    appendLine("\\n--- RAW OPTICAL FIELD (AGGREGATE) ---")
                    appendLine("Lambda1: ${result.lambda1} ± ${result.lambda1Std}")
                    appendLine("Lambda2: ${result.lambda2} ± ${result.lambda2Std}")
                    appendLine("Isotropic: ${result.isotropic} ± ${result.isotropicStd}")
                    appendLine("Anisotropic: ${result.anisotropic} ± ${result.anisotropicStd}")
                    appendLine("Tracked Dots: ${result.trackedDots}")
                    appendLine("Registration RMS: ${result.registrationRms}")
                    appendLine("Field Fit RMS: ${result.fieldFitRms}")
                    
                    result.allRuns.forEachIndexed { index, run ->
                        appendLine("\\n--- RUN ${index + 1} ---")
                        appendLine("Success: ${run.success}")
                        if (run.success) {
                            appendLine("Lambda1: ${run.lambda1}")
                            appendLine("Lambda2: ${run.lambda2}")
                            appendLine("Isotropic: ${run.isotropic}")
                            appendLine("Anisotropic: ${run.anisotropic}")
                            val rp = run.experimentalPower
                            appendLine("SPH EXP: ${rp?.sphere ?: "N/A"}")
                            appendLine("CYL EXP: ${rp?.cylinder ?: "N/A"}")
                            appendLine("AXIS EXP: ${rp?.axis ?: "N/A"}")
                            appendLine("Matches: ${run.trackedDots}")
                            appendLine("Reg RMS: ${run.registrationRms}")
                            appendLine("Fit RMS: ${run.fieldFitRms}")
                            appendLine("Coverage Pct: ${run.spatialCoveragePct}")
                        } else {
                            appendLine("Error: ${run.errorMessage}")
                        }
                    }
                }
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportStr))
                android.widget.Toast.makeText(context, "Data copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)) {"""

content = re.sub(pattern_button, replacement_button, content)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
