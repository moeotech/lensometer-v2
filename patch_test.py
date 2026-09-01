import re

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'r') as f:
    content = f.read()

import_statement = "import org.junit.Before\nimport org.opencv.android.OpenCVLoader\n"
content = re.sub(r'import org\.junit\.Test', import_statement + 'import org.junit.Test', content)

setup_method = """    @Before
    fun setUp() {
        OpenCVLoader.initDebug()
    }
    
"""
content = re.sub(r'class V6SyntheticTest \{', 'class V6SyntheticTest {\n' + setup_method, content)

with open('app/src/test/java/com/example/analysis/v6/V6SyntheticTest.kt', 'w') as f:
    f.write(content)
