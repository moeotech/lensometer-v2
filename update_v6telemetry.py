import re

with open('app/src/main/java/com/example/analysis/v6/V6Telemetry.kt', 'r') as f:
    content = f.read()

new_fields = """
    val autoRoiRejected: Boolean = false,
    val autoRoiRejectReason: String = "",
    val rawEllipseCenterX: Double = 0.0,
    val rawEllipseCenterY: Double = 0.0,
    val rawEllipseWidth: Double = 0.0,
    val rawEllipseHeight: Double = 0.0,
"""

content = content.replace('val lensRoiSource: String = "FALLBACK",', 'val lensRoiSource: String = "FALLBACK",\n' + new_fields)

with open('app/src/main/java/com/example/analysis/v6/V6Telemetry.kt', 'w') as f:
    f.write(content)
