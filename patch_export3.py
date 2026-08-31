import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

pattern = r"fun V4ResultDialog\(result: V4Result, onDismiss: \(\) -> Unit\) \{"
replacement = """fun V4ResultDialog(result: V4Result, onDismiss: () -> Unit) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current"""

content = re.sub(pattern, replacement, content)

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
