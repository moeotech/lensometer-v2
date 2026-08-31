import os

path = "app/src/main/java/com/example/analysis/V4OpticalAnalyzer.kt"
with open(path, "r") as f:
    text = f.read()

replacement = """    private fun assignGridTopology(points: List<Point>, spacing: Double, rejections: MutableMap<String, Int> = mutableMapOf()): Map<Pair<Int, Int>, Point> {
        if (points.isEmpty()) return emptyMap()

        val angles = mutableListOf<Double>()
        val edges = mutableListOf<Triple<Int, Int, Double>>()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val p1 = points[i]
                val p2 = points[j]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = hypot(dx, dy)
                if (dist > spacing * 0.7 && dist < spacing * 1.3) {
                    var a = Math.atan2(dy, dx)
                    while (a < 0) a += Math.PI / 2.0
                    while (a >= Math.PI / 2.0) a -= Math.PI / 2.0
                    angles.add(a)
                    edges.add(Triple(i, j, Math.atan2(dy, dx)))
                }
            }
        }

        val medianAngle = if (angles.isNotEmpty()) {
            angles.sort()
            angles[angles.size / 2]
        } else 0.0

        val cx = points.map { it.x }.average()
        val cy = points.map { it.y }.average()
        var bestSeedIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val d = hypot(points[i].x - cx, points[i].y - cy)
            if (d < bestDist) {
                bestDist = d
                bestSeedIdx = i
            }
        }

        val grid = mutableMapOf<Pair<Int, Int>, Point>()
        var assigned = 0
        var ambiguous = 0
        var collisions = 0

        val queue = ArrayDeque<Pair<Int, Pair<Int, Int>>>()
        val visited = mutableSetOf<Int>()
        val coordsToIdx = mutableMapOf<Pair<Int, Int>, Int>()
        
        if (bestSeedIdx != -1) {
            queue.addLast(Pair(bestSeedIdx, Pair(0, 0)))
            visited.add(bestSeedIdx)
            
            while (queue.isNotEmpty()) {
                val (currIdx, coord) = queue.removeFirst()
                val (row, col) = coord
                
                if (grid.containsKey(coord)) {
                    collisions++
                    ambiguous++
                    continue
                }
                
                grid[coord] = points[currIdx]
                coordsToIdx[coord] = currIdx
                assigned++
                
                // Find neighbors
                for (edge in edges) {
                    if (edge.first == currIdx || edge.second == currIdx) {
                        val nIdx = if (edge.first == currIdx) edge.second else edge.first
                        if (visited.contains(nIdx)) continue
                        
                        val nPt = points[nIdx]
                        val cPt = points[currIdx]
                        val dx = nPt.x - cPt.x
                        val dy = nPt.y - cPt.y
                        
                        // Transform delta to aligned space
                        val cosA = Math.cos(-medianAngle)
                        val sinA = Math.sin(-medianAngle)
                        val rx = dx * cosA - dy * sinA
                        val ry = dx * sinA + dy * cosA
                        
                        var dr = 0
                        var dc = 0
                        if (abs(rx) > abs(ry)) {
                            dc = if (rx > 0) 1 else -1
                        } else {
                            dr = if (ry > 0) 1 else -1
                        }
                        
                        val nCoord = Pair(row + dr, col + dc)
                        queue.addLast(Pair(nIdx, nCoord))
                        visited.add(nIdx)
                    }
                }
            }
        }
        
        rejections["topologyInputDots"] = points.size
        rejections["topologyAssignedDots"] = assigned
        rejections["topologyUnassignedDots"] = points.size - assigned
        rejections["topologyCollisions"] = collisions
        rejections["topologyLargestComponent"] = assigned
        rejections["topologyConsistencyErrors"] = ambiguous
        
        return grid
    }"""

start_str = "    private fun assignGridTopology(points: List<Point>, spacing: Double, rejections: MutableMap<String, Int> = mutableMapOf()): Map<Pair<Int, Int>, Point> {"
end_str = "        return grid\n    }"

start_idx = text.find(start_str)
end_idx = text.find(end_str, start_idx) + len(end_str)

target = text[start_idx:end_idx]
text = text.replace(target, replacement)

with open(path, "w") as f:
    f.write(text)
