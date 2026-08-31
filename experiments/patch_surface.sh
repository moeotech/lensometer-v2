for file in app/src/main/java/com/example/ui/ExperimentScreen.kt app/src/main/java/com/example/ui/FocusExperimentScreen.kt app/src/main/java/com/example/ui/V4ExperimentScreen.kt; do
    sed -i 's/val preview = Preview.Builder().build()/val preview = Preview.Builder().build()\n                        preview.setSurfaceProvider(previewView.surfaceProvider)/g' "$file"
done
