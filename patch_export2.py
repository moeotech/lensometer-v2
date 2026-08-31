import re

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    content = f.read()

# Fix the newlines inside double quotes
content = content.replace('appendLine("\\n--- EXPERIMENTAL POWER ---")', 'appendLine("")\n                    appendLine("--- EXPERIMENTAL POWER ---")')
content = content.replace('appendLine("\\n--- RAW OPTICAL FIELD (AGGREGATE) ---")', 'appendLine("")\n                    appendLine("--- RAW OPTICAL FIELD (AGGREGATE) ---")')
content = content.replace('appendLine("\\n--- RUN ${index + 1} ---")', 'appendLine("")\n                        appendLine("--- RUN ${index + 1} ---")')

# wait, I should also fix the actual ones that got inserted:
content = content.replace('appendLine("\n--- EXPERIMENTAL POWER ---")', 'appendLine("")\n                    appendLine("--- EXPERIMENTAL POWER ---")')
content = content.replace('appendLine("\n--- RAW OPTICAL FIELD (AGGREGATE) ---")', 'appendLine("")\n                    appendLine("--- RAW OPTICAL FIELD (AGGREGATE) ---")')
content = content.replace('appendLine("\n--- RUN ${index + 1} ---")', 'appendLine("")\n                        appendLine("--- RUN ${index + 1} ---")')

with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'w') as f:
    f.write(content)
