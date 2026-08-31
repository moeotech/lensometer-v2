import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()
            val candidateCoords = mutableListOf<Pair<Int, Int>>()

            val rejectedRefs = mutableListOf<Point>()
            for ((coord, ptRef) in gridMap) {
                if (acceptedMatches.containsKey(coord)) {
                    candidateRef.add(ptRef)
                    candidateLens.add(acceptedMatches[coord]!!)
                    candidateCoords.add(coord)
                } else {"""

text = text.replace("""            val candidateRef = mutableListOf<Point>()
            val candidateLens = mutableListOf<Point>()

            
            val rejectedRefs = mutableListOf<Point>()
            for ((coord, ptRef) in gridMap) {
                if (acceptedMatches.containsKey(coord)) {
                    candidateRef.add(ptRef)""", replacement)

# and update the analyzePoints calls
text = text.replace("""analyzePoints(fRef, fLens, w, h, baseRefPoints.size, fPts.size, spacing, mutableMapOf())""", """analyzePoints(fRef, fLens, w, h, baseRefPoints.size, fPts.size, spacing, mutableMapOf(), emptyList())""")
text = text.replace("""val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections)""", """val res = analyzePoints(candidateRef, candidateLens, w, h, baseRefPoints.size, baseLensPoints.size, spacing, rejections, candidateCoords)""")

with open(path, "w") as f:
    f.write(text)
