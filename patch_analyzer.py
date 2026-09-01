import re

with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'r') as f:
    content = f.read()

# Modify analyze function
analyze_old = r'''    suspend fun analyze\(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>\): V6Result = withContext\(Dispatchers\.Default\) \{
        if \(noLensFrames\.isEmpty\(\) \|\| withLensFrames\.isEmpty\(\)\) \{
            return@withContext V6Result\(V6Telemetry\(success = false, failureReason = "Missing frames"\)\)
        \}
        val refPoints = noLensFrames\.maxByOrNull \{ detectDots\(it\)\.size \}\?\.let \{ detectDots\(it\) \} \?: emptyList\(\)
        val lensPoints = withLensFrames\.maxByOrNull \{ detectDots\(it\)\.size \}\?\.let \{ detectDots\(it\) \} \?: emptyList\(\)
        if \(refPoints\.isEmpty\(\) \|\| lensPoints\.isEmpty\(\)\) \{
            return@withContext V6Result\(V6Telemetry\(success = false, failureReason = "No points detected"\)\)
        \}
        return@withContext analyzePoints\(refPoints, lensPoints, noLensFrames\[0\]\.width, noLensFrames\[0\]\.height\)
    \}'''

analyze_new = r'''    suspend fun analyze(noLensFrames: List<Bitmap>, withLensFrames: List<Bitmap>): V6Result = withContext(Dispatchers.Default) {
        if (noLensFrames.isEmpty() || withLensFrames.isEmpty()) {
            return@withContext V6Result(V6Telemetry(success = false, failureReason = "Missing frames"))
        }
        val refPoints = noLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()
        val lensPoints = withLensFrames.maxByOrNull { detectDots(it).size }?.let { detectDots(it) } ?: emptyList()
        if (refPoints.isEmpty() || lensPoints.isEmpty()) {
            return@withContext V6Result(V6Telemetry(success = false, failureReason = "No points detected"))
        }
        
        var roiSource = "FALLBACK"
        var roiCenterX = noLensFrames[0].width / 2.0
        var roiCenterY = noLensFrames[0].height / 2.0
        var roiInnerR = min(noLensFrames[0].width, noLensFrames[0].height) * 0.25
        var roiOuterR = min(noLensFrames[0].width, noLensFrames[0].height) * 0.40

        for (frame in withLensFrames) {
            val ell = com.example.ui.detectLensEllipse(frame)
            if (ell != null) {
                roiCenterX = ell.center.x
                roiCenterY = ell.center.y
                val minR = min(ell.size.width, ell.size.height) / 2.0
                val maxR = max(ell.size.width, ell.size.height) / 2.0
                roiInnerR = minR * 0.8
                roiOuterR = maxR * 1.2
                roiSource = "AUTO"
                break
            }
        }
        
        val providedRoi = V6LensRoi(roiCenterX, roiCenterY, roiInnerR, roiOuterR)
        return@withContext analyzePoints(refPoints, lensPoints, noLensFrames[0].width, noLensFrames[0].height, providedRoi, roiSource)
    }'''

content = re.sub(analyze_old, analyze_new, content)

# Modify analyzePoints function signature and ROI usage
analyzePoints_old = r'''    fun analyzePoints\(refPoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920\): V6Result \{
        val refGrid = V6GridDetector\.recoverZeroGrid\(refPoints\)
        val lensGrid = V6GridDetector\.recoverLensGrid\(lensPoints, refGrid\)
        
        val roi = V6LensRoi\(
            centerX = width / 2\.0,
            centerY = height / 2\.0,
            innerRadius = min\(width, height\) \* 0\.25,
            outerRadius = min\(width, height\) \* 0\.40
        \)'''

analyzePoints_new = r'''    fun analyzePoints(refPoints: List<Point>, lensPoints: List<Point>, width: Int = 1080, height: Int = 1920, providedRoi: V6LensRoi? = null, providedRoiSource: String = "FALLBACK"): V6Result {
        val refGrid = V6GridDetector.recoverZeroGrid(refPoints)
        val lensGrid = V6GridDetector.recoverLensGrid(lensPoints, refGrid)
        
        val roi = providedRoi ?: V6LensRoi(
            centerX = width / 2.0,
            centerY = height / 2.0,
            innerRadius = min(width, height) * 0.25,
            outerRadius = min(width, height) * 0.40
        )'''
content = re.sub(analyzePoints_old, analyzePoints_new, content)

# Modify telemetry creation
telemetry_old = r'''            gridOriginY = refGrid\.originY,
            referenceAssignedCells = refGrid\.validCellCount,'''

telemetry_new = r'''            gridOriginY = refGrid.originY,
            lensRoiCenterX = roi.centerX,
            lensRoiCenterY = roi.centerY,
            lensRoiInnerRadius = roi.innerRadius,
            lensRoiOuterRadius = roi.outerRadius,
            lensRoiSource = providedRoiSource,
            referenceAssignedCells = refGrid.validCellCount,'''

content = re.sub(telemetry_old, telemetry_new, content)

with open('app/src/main/java/com/example/analysis/v6/V6StructuredDeflectometryAnalyzer.kt', 'w') as f:
    f.write(content)
