awk '
/LaunchedEffect\(Unit\) \{/ {
    in_block = 1
    print
    next
}
in_block == 1 {
    if ($0 ~ /android\.util\.Log\.e\("OpenCV", "Initialization failed"\)/) {
        next
    }
    if ($0 ~ /^\s*\}\s*$/) {
        in_block = 2
        next
    }
}
in_block == 2 {
    if ($0 ~ /^\s*\}\s*$/) {
        in_block = 0
        next
    }
    in_block = 0
}
{
    print
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp.kt
mv temp.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
