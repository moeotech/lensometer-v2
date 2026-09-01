import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

# Replace the stray '}}' with '}'
content = content.replace("    }}\n\n    private fun generateGrid", "    }\n\n    private fun generateGrid")

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
