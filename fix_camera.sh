for file in app/src/main/java/com/example/ui/*ExperimentScreen.kt; do
    sed -i 's/CameraSelector.DEFAULT_BACK_CAMERA/if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA/g' "$file"
done
