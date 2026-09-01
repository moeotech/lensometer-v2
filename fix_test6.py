import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

# The class is closed prematurely at line 63
content = content.replace("    }\n\n}\n        \n    \n    \n    private fun", "    }\n\n    private fun")

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
