    suspend fun calculateRepeatability(results: List<V4RunResult>): V4Result = withContext(Dispatchers.Default) {
        if (results.size < 3) {
            return@withContext V4Result(success = false, errorMessage = "Need 3 runs")
        }
        
        if (results.any { !it.success }) {
            return@withContext V4Result(success = false, errorMessage = "One or more runs failed: " + results.first { !it.success }.errorMessage)
        }
        
        val l1_vals = results.map { it.lambda1 }.sorted()
        if (l1_vals.last() - l1_vals.first() > 0.15) {
            return@withContext V4Result(success = false, errorMessage = "REPEATABILITY FAILED (Lambda1 spread > 0.15)", allRuns = results, lastRunResult = results.last())
        }
        
        val lambda1Mean = results.map { it.lambda1 }.average()
        val lambda2Mean = results.map { it.lambda2 }.average()
        val isotropicMean = results.map { it.isotropic }.average()
        val anisotropicMean = results.map { it.anisotropic }.average()
        
        val lambda1Std = sqrt(results.map { (it.lambda1 - lambda1Mean) * (it.lambda1 - lambda1Mean) }.average())
        val lambda2Std = sqrt(results.map { (it.lambda2 - lambda2Mean) * (it.lambda2 - lambda2Mean) }.average())
        val isotropicStd = sqrt(results.map { (it.isotropic - isotropicMean) * (it.isotropic - isotropicMean) }.average())
        val anisotropicStd = sqrt(results.map { (it.anisotropic - anisotropicMean) * (it.anisotropic - anisotropicMean) }.average())
        
        var axisDisplay = "UNRELIABLE"
        var axisMean = 0.0
        if (anisotropicMean > 0.02) {
            var sinSum = 0.0
            var cosSum = 0.0
            for (r in results) {
                val rad = r.axis * 2.0 * Math.PI / 180.0
                sinSum += sin(rad)
                cosSum += cos(rad)
            }
            axisMean = atan2(sinSum / results.size, cosSum / results.size) * 180.0 / (2.0 * Math.PI)
            if (axisMean < 0) axisMean += 180.0
            if (axisMean >= 180.0) axisMean -= 180.0
            axisDisplay = String.format("%.0f° SIGNAL", axisMean)
        }
        
        val lastRun = results.last()
        val visualVectorMap = if (lastRun.referencePoints.isNotEmpty()) { 
             drawVectorMapInternal(lastRun, 1f)
        } else null
        
        return@withContext V4Result(
            success = true,
            sphDisplay = "NOT CALIBRATED",
            cylDisplay = "NOT CALIBRATED",
            axisDisplay = axisDisplay,
            lambda1 = lambda1Mean,
            lambda2 = lambda2Mean,
            isotropic = isotropicMean,
            anisotropic = anisotropicMean,
            lambda1Std = lambda1Std,
            lambda2Std = lambda2Std,
            isotropicStd = isotropicStd,
            anisotropicStd = anisotropicStd,
            allRuns = results,
            trackedDots = lastRun.trackedDots,
            registrationRms = lastRun.registrationRms,
            ransacInliers = lastRun.ransacInliers,
            fieldFitRms = lastRun.fieldFitRms,
            refDotCount = lastRun.refDotCount,
            lensDotCount = lastRun.lensDotCount,
            meanDx = lastRun.meanDx,
            meanDy = lastRun.meanDy,
            visualVectorMap = visualVectorMap,
            lastRunResult = lastRun,
            globalScaleAmbiguous = results.any { it.globalScaleAmbiguous }
        )
    }
    
    fun drawVectorMap(result: V4Result, mag: Float): Bitmap? {
        if (result.lastRunResult == null) return null
        return drawVectorMapInternal(result.lastRunResult, mag)
    }
    
    private fun drawVectorMapInternal(run: V4RunResult, mag: Float): Bitmap {
        if (run.refWidth <= 0 || run.refHeight <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(run.refWidth, run.refHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        
        val paintRef = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
        val paintLens = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL; isAntiAlias = true }
        val paintArrow = Paint().apply { color = Color.YELLOW; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
        
        val sizeRef = run.referencePoints.size
        val sizeLens = run.observedPoints.size
        val limit = min(sizeRef, sizeLens)
        
        for (i in 0 until limit) {
            val refPt = run.referencePoints[i]
            val lensPt = run.observedPoints[i]
            
            canvas.drawCircle(refPt.x.toFloat(), refPt.y.toFloat(), 3f, paintRef)
            
            val dx = (lensPt.x - refPt.x) * mag
            val dy = (lensPt.y - refPt.y) * mag
            
            canvas.drawCircle((refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), 3f, paintLens)
            canvas.drawLine(refPt.x.toFloat(), refPt.y.toFloat(), (refPt.x + dx).toFloat(), (refPt.y + dy).toFloat(), paintArrow)
        }
        
        return bitmap
    }
}
