import os
path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

text = text.replace("runResults.fill(null)", "runResults.clear(); runResults.add(null); runResults.add(null); runResults.add(null)")

with open(path, "w") as f:
    f.write(text)
