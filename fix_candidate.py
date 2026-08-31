import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

target = """            val rejectedRefs = mutableListOf<Point>()
            for ((coord, ptRef) in gridMap) {
                if (acceptedMatches.containsKey(coord)) {
                    candidateRef.add(ptRef)
                    candidateLens.add(acceptedMatches[coord]!!)
                    candidateCoords.add(coord)
                } else {
                    candidateLens.add(acceptedMatches[coord]!!)
                } else {
                    rejectedRefs.add(ptRef)
                    rejections["topology_rejection"] = rejections.getOrDefault("topology_rejection", 0) + 1
                }
            }"""

replacement = """            val rejectedRefs = mutableListOf<Point>()
            for ((coord, ptRef) in gridMap) {
                if (acceptedMatches.containsKey(coord)) {
                    candidateRef.add(ptRef)
                    candidateLens.add(acceptedMatches[coord]!!)
                    candidateCoords.add(coord)
                } else {
                    rejectedRefs.add(ptRef)
                    rejections["topology_rejection"] = rejections.getOrDefault("topology_rejection", 0) + 1
                }
            }"""

text = text.replace(target, replacement)
with open(path, "w") as f:
    f.write(text)
