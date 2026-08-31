import re

with open('app/src/test/java/com/example/analysis/V4OpticalAnalyzerTest.kt', 'r') as f:
    content = f.read()

# Match the class declaration
class_match = re.search(r'class V4OpticalAnalyzerTest \{(.*?)\Z', content, flags=re.DOTALL)
if not class_match:
    print("Class not found")
    exit(1)

body = class_match.group(1)

# Find all test methods
test_pattern = r'(\s*@Test\s+fun (test[A-Z0-9_a-z]+)\(.*?)(?=\s*@Test|\Z)'
tests = re.findall(test_pattern, body, flags=re.DOTALL)

unique_tests = {}
for full_text, name in tests:
    if name not in unique_tests:
        # cleanup double braces
        clean_text = re.sub(r'\}\}$', '}', full_text.strip())
        unique_tests[name] = clean_text

header = content[:class_match.start()]
class_decl = "class V4OpticalAnalyzerTest {\n    init {\n        OpenCVLoader.initLocal()\n    }\n"

with open('app/src/test/java/com/example/analysis/V4OpticalAnalyzerTest.kt', 'w') as f:
    f.write(header)
    f.write(class_decl)
    for name, text in unique_tests.items():
        # fix mismatched braces if any
        open_b = text.count('{')
        close_b = text.count('}')
        if open_b > close_b:
            text += '}' * (open_b - close_b)
        elif close_b > open_b:
            # remove excess
            for _ in range(close_b - open_b):
                text = text.rstrip().rstrip('}')
        
        f.write("\n    @Test\n")
        # replace the @Test fun part to remove existing @Test if any
        clean_text = re.sub(r'^@Test\s+', '', text)
        f.write("    " + clean_text + "\n")
    f.write("}\n")
