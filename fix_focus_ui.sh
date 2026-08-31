awk '
/Box\(modifier = Modifier\.fillMaxSize\(\)\)/ {
    skip = 1
}
/^}$/ {
    if (skip) {
        skip = 0
        print "    Box(modifier = Modifier.fillMaxSize()) {"
        print "        androidx.compose.ui.viewinterop.AndroidView("
        print "            factory = { previewView },"
        print "            modifier = Modifier.fillMaxSize()"
        print "        )"
        print "    }"
        print "}"
        next
    }
}
{
    if (!skip) print
}
' app/src/main/java/com/example/ui/FocusExperimentScreen.kt > temp_focus2.kt
mv temp_focus2.kt app/src/main/java/com/example/ui/FocusExperimentScreen.kt
