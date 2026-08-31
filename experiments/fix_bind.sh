#!/bin/bash
for file in app/src/main/java/com/example/ui/ExperimentScreen.kt app/src/main/java/com/example/ui/FocusExperimentScreen.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt; do
    sed -i 's/previewRef?.let { cameraProvider.unbind(it) }; imageAnalysisRef?.let { cameraProvider.unbind(it) }/cameraProvider.unbindAll()/g' $file
done
