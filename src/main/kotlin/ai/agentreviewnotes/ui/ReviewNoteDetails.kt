package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewNote

internal data class ReviewNoteDetailRow(val label: String, val value: String)

internal object ReviewNoteDetails {
    fun rows(note: ReviewNote): List<ReviewNoteDetailRow> {
        val directory = note.location.target == "directory"
        val target = if (directory) "Каталог: ${note.location.workspacePath}" else "Файл: ${note.location.workspacePath}"
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
        } ?: "Вне Git"
        return listOf(
            ReviewNoteDetailRow("ID", note.id),
            ReviewNoteDetailRow("Цель", target),
            ReviewNoteDetailRow("Строки", lines),
            ReviewNoteDetailRow("Сниппет", snippet),
            ReviewNoteDetailRow("Репозиторий", repository),
            ReviewNoteDetailRow("Ветка", note.location.branch ?: "—"),
            ReviewNoteDetailRow("Git snapshot", note.location.head ?: "—"),
            ReviewNoteDetailRow("Создано", note.createdAt),
            ReviewNoteDetailRow("Решено", note.resolution?.resolvedAt ?: "—"),
            ReviewNoteDetailRow("Тип", note.kind),
            ReviewNoteDetailRow("Статус", note.status),
            ReviewNoteDetailRow("Замечание", note.message),
        )
    }
}
