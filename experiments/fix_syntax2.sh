#!/bin/bash
sed -i '/LaunchedEffect(Unit) {/d' app/src/main/java/com/example/ui/LensExperimentScreen.kt
sed -i '/^        }$/d' app/src/main/java/com/example/ui/LensExperimentScreen.kt
sed -i '/^    }$/d' app/src/main/java/com/example/ui/LensExperimentScreen.kt
