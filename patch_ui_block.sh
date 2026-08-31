cat << 'INNER_EOF' > temp_ui.kt
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (phase == LensExperimentPhase.ALIGN_NO_LENS || phase == LensExperimentPhase.ALIGN_LENS) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewRef?.setSurfaceProvider(this.surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    previewRef?.setSurfaceProvider(view.surfaceProvider)
                }
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val radius = min(size.width, size.height) * 0.45f
                
                if (phase == LensExperimentPhase.ALIGN_NO_LENS) {
                    drawCircle(
                        color = Color.Yellow,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f)
                    )
                    drawLine(Color.Red, Offset(cx - 20, cy), Offset(cx + 20, cy), 2f)
                    drawLine(Color.Red, Offset(cx, cy - 20), Offset(cx, cy + 20), 2f)
                } else {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(path, Color.White)
                    
                    val ringColor = if (detectedEllipse == null) Color.LightGray
                    else if (isStable) Color.Green
                    else if (alignMessage == "STABILIZING...") Color(0xFFFFA500)
                    else Color.Red
                    
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 12f)
                    )
                    
                    if (progress > 0f) {
                        drawArc(
                            color = Color(0xFF006400),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = 12f)
                        )
                    }
                    
                    val ell = detectedEllipse
                    if (ell != null && imgW > 1 && imgH > 1) {
                        val scaleX = size.width / imgW
                        val scaleY = size.height / imgH
                        val rrx = ell.center.x * scaleX
                        val rry = ell.center.y * scaleY
                        val rw = ell.size.width * scaleX / 2.0f
                        val rh = ell.size.height * scaleY / 2.0f
                        
                        withTransform({
                            translate(rrx.toFloat(), rry.toFloat())
                            rotate(ell.angle.toFloat())
                        }) {
                            drawOval(
                                color = if (isStable) Color.Green else Color.Red,
                                topLeft = Offset(-rw.toFloat(), -rh.toFloat()),
                                size = androidx.compose.ui.geometry.Size((rw*2).toFloat(), (rh*2).toFloat()),
                                style = Stroke(width = 6f)
                            )
                            drawLine(Color.White, Offset(-15f, 0f), Offset(15f, 0f), 4f)
                            drawLine(Color.White, Offset(0f, -15f), Offset(0f, 15f), 4f)
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (phase == LensExperimentPhase.ALIGN_NO_LENS) "ALIGN PRINTED TARGET (NO LENS)" else alignMessage,
                    color = if (phase == LensExperimentPhase.ALIGN_LENS) Color.Black else Color.White,
                    fontSize = if (phase == LensExperimentPhase.ALIGN_LENS) 24.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = if (phase == LensExperimentPhase.ALIGN_LENS) Modifier.padding(16.dp) else Modifier.background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(16.dp)
                )
                if (phase == LensExperimentPhase.ALIGN_NO_LENS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                phase = LensExperimentPhase.CAPTURE_NO_LENS
                                runCaptureSequence(noLensFrames, lockAE = true)
                                phase = LensExperimentPhase.ALIGN_LENS
                            }
                        },
                        enabled = isStable
                    ) {
                        Text("CAPTURE BASE")
                    }
                }
            }
INNER_EOF

awk '
/Box\(modifier = Modifier\.fillMaxSize\(\)\.background\(Color\.Black\)\)/ {
    in_block = 1
    system("cat temp_ui.kt")
    next
}
/\} else if \(phase == LensExperimentPhase\.CAPTURE_NO_LENS/ {
    if (in_block) {
        in_block = 0
        print "        } else if (phase == LensExperimentPhase.CAPTURE_NO_LENS || phase == LensExperimentPhase.CAPTURE_LENS || phase == LensExperimentPhase.PROCESSING) {"
        next
    }
}
{
    if (!in_block) print
}
' app/src/main/java/com/example/ui/LensExperimentScreen.kt > temp_screen.kt
mv temp_screen.kt app/src/main/java/com/example/ui/LensExperimentScreen.kt
