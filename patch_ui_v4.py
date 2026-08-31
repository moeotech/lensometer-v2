import os
path = "app/src/main/java/com/example/ui/V4ExperimentScreen.kt"
with open(path, "r") as f:
    text = f.read()

text = text.replace("currentStep = V4Step.STEP_1_NO_LENS\n                                noLensFrames.clear()\n                                withLensFrames.clear()", "currentStep = V4Step.STEP_2_WITH_LENS\n                                withLensFrames.clear()")
text = text.replace("currentStep = V4Step.STEP_1_NO_LENS\n                                    noLensFrames.clear()\n                                    withLensFrames.clear()", "currentStep = V4Step.STEP_2_WITH_LENS\n                                    withLensFrames.clear()")

with open(path, "w") as f:
    f.write(text)
