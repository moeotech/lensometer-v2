for file in app/src/main/java/com/example/ui/*ExperimentScreen.kt; do
  sed -i 's/provider.unbindAll()/try { imageAnalysisRef?.let { provider.unbind(it) }; previewRef?.let { provider.unbind(it) } } catch(e: Exception) {}/g' "$file"
done
