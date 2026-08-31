#!/bin/bash
awk '
/bestEllipse = ellipse/ {
    print
    print "            }"
    print "        }"
    print "    }"
    print "    mat.release(); gray.release(); edges.release(); hierarchy.release()"
    print "    return bestEllipse"
    print "}"
    exit
}
{
    print
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp.kt
mv temp.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
