package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewNote

internal data class ReviewNoteDetailRow(val label: String, val value: String)

internal object ReviewNoteDetails {
    fun rows(note: ReviewNote): List<ReviewNoteDetailRow> {
        val directory = note.location.target == "directory"
        val target = if (directory) "Directory: ${note.location.workspacePath}" else "File: ${note.location.workspacePath}"
        val lines = when {
            directory -> "—"
            note.location.startLine == note.location.endLine -> note.location.startLine.toString()
            else -> "${note.location.startLine}–${note.location.endLine}"
        }
        val snippet = note.anchor.selection.ifBlank {
            (note.anchor.prefix + note.anchor.suffix).trim()
        }.ifBlank { "—" }
        val repository = note.location.vcsRoot?.let { root ->
            note.location.vcsPath?.let { path -> "$root ($path)" } ?: root
        } ?: "Outside Git"
        return listOf(
            ReviewNoteDetailRow("ID", note.id),
            ReviewNoteDetailRow("Target", target),
            ReviewNoteDetailRow("Lines", lines),
            ReviewNoteDetailRow("Snippet", snippet),
            ReviewNoteDetailRow("Repository", repository),
            ReviewNoteDetailRow("Branch", note.location.branch ?: "—"),
            ReviewNoteDetailRow("Git snapshot", note.location.head ?: "—"),
            ReviewNoteDetailRow("Created", note.createdAt),
            ReviewNoteDetailRow("Resolved", note.resolution?.resolvedAt ?: "—"),
            ReviewNoteDetailRow("Type", note.kind),
            ReviewNoteDetailRow("Status", note.status),
            ReviewNoteDetailRow("Tags", note.tags.joinToString().ifBlank { "—" }),
            ReviewNoteDetailRow("Depends on", note.dependsOn.joinToString().ifBlank { "—" }),
            ReviewNoteDetailRow("Note", note.message),
        )
    }
}
