                                            if (cx < w * 0.4) alignMessage = "MOVE RIGHT"
                                            else if (cx > w * 0.6) alignMessage = "MOVE LEFT"
                                            else if (cy < h * 0.4) alignMessage = "MOVE DOWN"
                                            else if (cy > h * 0.6) alignMessage = "MOVE UP"
                                            else {
                                                // 2. Check Size (Distance)
                                                val sizeRatio = max(ell.size.width, ell.size.height) / min(w, h).toDouble()
                                                if (sizeRatio < 0.45) {
                                                    alignMessage = "MOVE CLOSER"
                                                    isStable = false
                                                } else if (sizeRatio > 0.65) {
                                                    alignMessage = "MOVE FARTHER"
                                                    isStable = false
                                                } else {
                                                    // 3. Check Tilt (Aspect Ratio)
                                                    val aspect = max(ell.size.width, ell.size.height) / min(ell.size.width, ell.size.height)
                                                    if (aspect > 1.3) {
                                                        alignMessage = "REDUCE TILT"
                                                        isStable = false
                                                    } else {
                                                        // 4. Temporal Stability
                                                        if (ellipseHistory.size == 10) {
                                                            val meanCx = ellipseHistory.map { it.center.x }.average()
                                                            val meanCy = ellipseHistory.map { it.center.y }.average()
                                                            val meanW = ellipseHistory.map { it.size.width }.average()
                                                            val meanH = ellipseHistory.map { it.size.height }.average()
                                                            val meanAngle = ellipseHistory.map { it.angle }.average()
                                                            
                                                            val stdCx = sqrt(ellipseHistory.map { (it.center.x - meanCx).pow(2) }.average())
                                                            val stdCy = sqrt(ellipseHistory.map { (it.center.y - meanCy).pow(2) }.average())
                                                            val stdW = sqrt(ellipseHistory.map { (it.size.width - meanW).pow(2) }.average())
                                                            val stdH = sqrt(ellipseHistory.map { (it.size.height - meanH).pow(2) }.average())
                                                            val stdAngle = sqrt(ellipseHistory.map { (it.angle - meanAngle).pow(2) }.average())
                                                            
                                                            if (stdCx <= 15.0 && stdCy <= 15.0 && stdW <= 15.0 && stdH <= 15.0 && stdAngle <= 7.0) {
                                                                alignMessage = "HOLD STILL"
                                                                isStable = true
                                                                stableLensGeom = LensGeometry(meanCx, meanCy, meanW, meanH, meanAngle)
                                                            } else {
                                                                alignMessage = "STABILIZING..."
                                                                isStable = false
                                                            }
                                                        } else {
                                                            alignMessage = "STABILIZING..."
                                                            isStable = false
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            alignMessage = "PLACE LENS IN CIRCLE"
                                            isStable = false
                                            ellipseHistory.clear()
                                        }
