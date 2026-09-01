import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

content = re.sub(r'OpenCVLoader\.initDebug\(\)\s*@Test', 'OpenCVLoader.initDebug()\n    }\n\n    @Test', content)

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
