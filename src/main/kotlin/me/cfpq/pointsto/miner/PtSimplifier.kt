package me.cfpq.pointsto.miner

/**
 * Interface responsible for reducing number of vertices of type [PtTempVertex] and [PtLocalVar]
 * in a graph for points-to analysis
 */
interface PtSimplifier {
    fun simplify(edges: Set<PtEdge>): Set<PtEdge>
}

object NoopPtSimplifier : PtSimplifier {
    override fun simplify(edges: Set<PtEdge>): Set<PtEdge> = edges
}

/**
 * Straightforward, but slow [PtSimplifier] implementation, intended to run on small graphs.
 * Great for debugging and testing.
 */
object SlowPtSimplifier : PtSimplifier {
    override fun simplify(edges: Set<PtEdge>): Set<PtEdge> {
        val newEdges = mutableSetOf<PtEdge>()
        val vertices = edges.flatMap { listOf(it.lhs, it.rhs) }.toSet()
        val adjList = edges.groupBy { it.lhs }

        // eliminate PtAssignEdges adjacent to isTemp() vertices
        // similarly to how epsilon edges are eliminated when converting e-NFA to NFA
        vertices.forEach { source ->
            fun PtVertex.canOccurOnSimplificationPath() = isTemp() || this == source

            /**
             * BFS starting from [start] vertex that is only allowed
             * to go along assign edges that `canOccurOnSimplificationPath`
             */
            fun bfs(start: PtVertex): Set<PtVertex> {
                val visited = mutableSetOf<PtVertex>()
                val queue = ArrayDeque(listOf(start))
                while (queue.isNotEmpty()) {
                    val vertex = queue.removeFirst()
                    visited.add(vertex)
                    if (vertex.canOccurOnSimplificationPath()) {
                        queue.addAll(
                            adjList[vertex].orEmpty()
                                .filterIsInstance<PtAssignEdge>()
                                .map { it.rhs }
                                .filter { it !in visited }
                        )
                    }
                }
                return visited
            }

            bfs(source).forEach { reachable ->
                newEdges.add(PtAssignEdge(source, reachable))
                if (reachable.canOccurOnSimplificationPath()) {
                    val edgesFromReachable = adjList[reachable].orEmpty()
                    edgesFromReachable.forEach { edge ->
                        bfs(edge.rhs).forEach { nonAssignReachable ->
                            newEdges.add(edge.copy(lhs = source, rhs = nonAssignReachable))
                        }
                    }
                }
            }
        }
        newEdges.removeIf { it is PtAssignEdge && (it.lhs.isTemp() || it.rhs.isTemp() || it.lhs == it.rhs) }
        val newAdjList = newEdges.associateBy { it.lhs }
        val reverseNewAdjList = newEdges.associateBy { it.rhs }
        val uselessTempVertices = vertices
            .filter { v -> v.isTemp() }
            .filter { v -> v !in newAdjList || v !in reverseNewAdjList }
            .toSet()
        return newEdges.filterNot { it.lhs in uselessTempVertices || it.rhs in uselessTempVertices }.toSet()
    }

    private fun PtVertex.isTemp() = this is PtTempVertex || this is PtLocalVar
}
