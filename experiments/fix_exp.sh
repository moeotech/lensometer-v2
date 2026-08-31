awk '
/val bitmap = proxyToBitmap\(imageProxy\)/ {
    skip = 1
}
/imageProxy\.close\(\)/ {
    skip = 0
    print "                                    frameCaptureCallback?.invoke(imageProxy)"
    print "                                } finally {"
    print "                                    imageProxy.close()"
    next
}
{
    if (!skip) print
}
' app/src/main/java/com/example/ui/ExperimentScreen.kt > temp_exp.kt
mv temp_exp.kt app/src/main/java/com/example/ui/ExperimentScreen.kt
