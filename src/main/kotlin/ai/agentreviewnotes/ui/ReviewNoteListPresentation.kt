package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote

internal data class ReviewNoteListRow(
    val prefix: String,
    val messagePreview: String,
    val text: String,
    val toolTip: String,
)

internal object ReviewNoteListPresentation {
    private const val MAX_MESSAGE_CODE_POINTS = 100

    fun row(note: ReviewNote): ReviewNoteListRow {
        val type = ReviewKind.entries.firstOrNull { it.wireValue == note.kind }?.title
            ?: note.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
        val status = note.status.replace('_', ' ')
        val branch = note.location.branch ?: "—"
        val name = note.location.workspacePath.trimEnd('/').substringAfterLast('/')
        val location = if (note.location.target == "directory") "$name/" else "$name:${note.location.startLine}"
        val tags = note.tags.joinToString(",")
        val prefix = listOf(type, status, branch, location, tags)
            .filter(String::isNotEmpty)
            .joinToString(" · ")
        val preview = messagePreview(note.message)
        return ReviewNoteListRow(
            prefix = prefix,
            messagePreview = preview,
            text = "$prefix — $preview",
            toolTip = note.message,
        )
    }

    fun messagePreview(message: String): String {
        val compact = message.replace(WHITESPACE, " ").trim()
        val codePoints = compact.codePointCount(0, compact.length)
        if (codePoints <= MAX_MESSAGE_CODE_POINTS) return compact
        val end = compact.offsetByCodePoints(0, MAX_MESSAGE_CODE_POINTS - 1)
        return compact.substring(0, end) + "…"
    }

    private val WHITESPACE = Regex("\\s+")
}
