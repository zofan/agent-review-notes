package ai.agentreviewnotes.projectview

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus

internal object ReviewNotePresence {
    private val inactiveStatuses = setOf(
        ReviewStatus.RESOLVED.wireValue,
        ReviewStatus.WONT_FIX.wireValue,
    )

    fun hasActiveNote(workspacePath: String, directory: Boolean, notes: List<ReviewNote>): Boolean {
        val normalized = workspacePath.trimEnd('/')
        val childPrefix = if (normalized.isEmpty()) "" else "$normalized/"
        return notes.any { note ->
            note.status !in inactiveStatuses &&
                (note.location.workspacePath == normalized ||
                    (directory && note.location.workspacePath.startsWith(childPrefix)))
        }
    }
}
