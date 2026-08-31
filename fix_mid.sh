awk '
/val lifecycle = lifecycleOwner.lifecycle/ {
    print "        }"
    print "    }"
    print
    next
}
{
    print
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp.kt
mv temp.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
