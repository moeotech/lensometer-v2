awk '
/@Composable/ {
    if (in_result_row) {
        print
        next
    }
}
/@Composable\nfun ResultRow\(label: String, value: String\)/ {
    in_result_row = 1
    next
}
/fun ResultRow\(label: String, value: String\)/ {
    in_result_row = 1
    next
}
{
    if (in_result_row && $0 ~ /^}$/) {
        in_result_row = 0
        next
    }
    if (!in_result_row) print
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp_screen.kt
mv temp_screen.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
