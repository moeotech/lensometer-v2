awk '
/val bitmap = proxyToBitmap\(imageProxy\)/ {
    skip = 1
}
/imageProxy\.close\(\)/ {
    skip = 0
    print "                                    val sharpness = com.example.analysis.FocusAnalyzer.measureCenterSharpness(imageProxy)"
    print "                                } finally {"
    print "                                    imageProxy.close()"
    next
}
{
    if (!skip) print
}
' app/src/main/java/com/example/ui/FocusExperimentScreen.kt > temp_focus.kt
mv temp_focus.kt app/src/main/java/com/example/ui/FocusExperimentScreen.kt
