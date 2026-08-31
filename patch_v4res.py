import os
import re
...
            if (opticalCenterValid) {
...
            } else {
                opticalCenterX = Double.NaN
                opticalCenterY = Double.NaN
            }
"""
text = re.sub(r'            // Optical center calculation\n.*?var minX = Double.MAX_VALUE', rep_center + "\n            var minX = Double.MAX_VALUE", text, flags=re.DOTALL)
