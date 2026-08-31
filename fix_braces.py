with open('app/src/main/java/com/example/ui/V4ExperimentScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "when" in line or "V4Step.COMPLETE" in line or "Text(\"MEASUREMENT COMPLETE" in line:
        pass
