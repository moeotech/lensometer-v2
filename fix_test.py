import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

fixed_content = content.replace("OpenCVLoader.initDebug()        @Test", "OpenCVLoader.initDebug()\n    }\n\n    @Test")

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(fixed_content)
