import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

# Remove the extra `}}` before `private fun generateGrid`
content = re.sub(r'    \}\}\s*private fun generateGrid', '    }\n\n    private fun generateGrid', content)

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
