#!/bin/bash
for f in app/src/main/java/com/example/ui/*.kt; do
  sed -i 's/if (previewRef != null) provider.unbind(previewRef)/provider.unbindAll()/g' "$f"
  sed -i 's/if (imageAnalysisRef != null) provider.unbind(imageAnalysisRef)//g' "$f"
done
