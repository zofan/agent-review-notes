package ai.agentreviewnotes.model

object ReviewNoteDependencyGraph {
    fun validate(notes: List<ReviewNote>) {
        val byId = notes.associateBy(ReviewNote::id)
        require(byId.size == notes.size) { "Review note ids must be unique" }
        val incoming = HashMap<String, Int>(notes.size)
        val dependents = HashMap<String, MutableList<String>>(notes.size)
        notes.forEach { note ->
            incoming[note.id] = note.dependsOn.size
            note.dependsOn.forEach { dependencyId ->
                require(dependencyId in byId) { "Missing review note dependency $dependencyId" }
                dependents.getOrPut(dependencyId, ::mutableListOf).add(note.id)
            }
        }
        val ready = ArrayDeque(incoming.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val noteId = ready.removeFirst()
            visited++
            dependents[noteId].orEmpty().forEach { dependentId ->
                val remaining = incoming.getValue(dependentId) - 1
                incoming[dependentId] = remaining
                if (remaining == 0) ready.addLast(dependentId)
            }
        }
        require(visited == notes.size) { "Review note dependency graph contains a cycle" }
    }
}
