package ai.agentreviewnotes.ui

import java.util.concurrent.CopyOnWriteArrayList

internal class ReviewNoteSelectionModel {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile
    private var pendingId: String? = null

    fun request(noteId: String) {
        pendingId = noteId
        listeners.forEach { listener -> listener(noteId) }
    }

    fun subscribe(listener: (String) -> Unit): AutoCloseable {
        listeners.add(listener)
        pendingId?.let(listener)
        return AutoCloseable { listeners.remove(listener) }
    }
}
