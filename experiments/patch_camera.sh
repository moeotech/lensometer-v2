for file in app/src/main/java/com/example/ui/*ExperimentScreen.kt; do
    sed -i 's/provider\.unbindAll()/\/\/ provider.unbindAll() \/\/ Removed to prevent unbinding the next screen'"'"'s camera/g' "$file"
done
