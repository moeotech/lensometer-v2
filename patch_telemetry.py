import re

with open('app/src/main/java/com/example/analysis/v6/V6Telemetry.kt', 'r') as f:
    content = f.read()

# Add LensRoi properties
new_props = """
    val lensRoiCenterX: Double = 0.0,
    val lensRoiCenterY: Double = 0.0,
    val lensRoiInnerRadius: Double = 0.0,
    val lensRoiOuterRadius: Double = 0.0,
    val lensRoiSource: String = "FALLBACK",
"""

content = re.sub(r'val gridOriginY: Double = 0\.0,', 'val gridOriginY: Double = 0.0,' + new_props, content)

with open('app/src/main/java/com/example/analysis/v6/V6Telemetry.kt', 'w') as f:
    f.write(content)
