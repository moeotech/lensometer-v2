import os
import glob

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 1. Remove cameraProvider.unbindAll() from addListener
    content = content.replace("cameraProvider.unbindAll()\n", "")
    content = content.replace("cameraProvider.unbindAll()\r\n", "")
    content = content.replace("cameraProvider.unbindAll()", "")

    # 2. In onDispose, replace provider.unbindAll() with unbinding specific refs
    # Note: my previous shell script replaced it with `provider.unbindAll()`
    # Let's replace `provider.unbindAll()` inside `onDispose` with:
    # if (previewRef != null) provider.unbind(previewRef)
    # if (imageAnalysisRef != null) provider.unbind(imageAnalysisRef)
    
    # We need to find the onDispose block and replace provider.unbindAll()
    replacement = """                if (previewRef != null) provider.unbind(previewRef)
                if (imageAnalysisRef != null) provider.unbind(imageAnalysisRef)"""
    
    content = content.replace("provider.unbindAll()", replacement)

    with open(filepath, 'w') as f:
        f.write(content)

for filepath in glob.glob("app/src/main/java/com/example/ui/*.kt"):
    fix_file(filepath)

print("Camera setup fixed.")
