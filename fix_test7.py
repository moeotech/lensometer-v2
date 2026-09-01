import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

# Lines 63 and 181 both have '}'. The class should encapsulate everything.
# Let's remove the '}' at line 63
content = content.replace("    }\n\n}\n        \n    \n    \n    private fun", "    }\n\n    private fun")
# Since the previous fix didn't work (probably because of spacing), let's do this robustly:
lines = content.split('\n')
for i in range(len(lines)):
    if lines[i] == '}':
        # if it's not the last line
        if i < len(lines) - 1:
            lines[i] = ''

new_content = '\n'.join(lines)

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(new_content)
