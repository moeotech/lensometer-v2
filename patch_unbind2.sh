for file in app/src/main/java/com/example/ui/*ExperimentScreen.kt; do
  sed -i 's/cameraProvider.unbindAll()//g' "$file"
done
