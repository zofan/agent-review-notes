package ai.agentreviewnotes.anchor

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReviewNoteAnchorTest {
    @Test
    fun `точный hash сохраняет исходную позицию`() {
        val text = "alpha\nbeta\ngamma"
        val note = note(text, selection = "beta", offset = 6)

        val result = assertIs<AnchorResult.Resolved>(ReviewNoteAnchor.resolve(note, text))

        assertEquals(6, result.offset)
    }

    @Test
    fun `уникальный фрагмент перепривязывается после внешней вставки`() {
        val original = "alpha\nbeta\ngamma"
        val note = note(original, selection = "beta", offset = 6)

        val result = assertIs<AnchorResult.Resolved>(ReviewNoteAnchor.resolve(note, "prefix\n$original"))

        assertEquals(13, result.offset)
    }

    @Test
    fun `неоднозначный фрагмент блокирует автоматическую привязку`() {
        val original = "alpha\nbeta\ngamma"
        val note = note(original, selection = "beta", offset = 6)

        val result = assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, "beta\n$original"))

        assertEquals("Выделенный фрагмент встречается несколько раз", result.reason)
    }

    @Test
    fun `контекст различает повторяющийся фрагмент`() {
        val original = "left beta right\nother beta other"
        val note = note(original, selection = "beta", offset = 5, prefix = "left ", suffix = " right")
        val current = "other beta other\ninserted\nleft beta right"

        val result = assertIs<AnchorResult.Resolved>(ReviewNoteAnchor.resolve(note, current))

        assertEquals(current.lastIndexOf("beta"), result.offset)
    }

    @Test
    fun `исчезнувший фрагмент блокирует автоматическую привязку`() {
        val original = "alpha\nbeta\ngamma"
        val note = note(original, selection = "beta", offset = 6)

        val result = assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, "alpha\ndelta\ngamma"))

        assertEquals("Выделенный фрагмент больше не найден", result.reason)
    }

    private fun note(
        text: String,
        selection: String,
        offset: Int,
        prefix: String = "",
        suffix: String = "",
    ): ReviewNote = ReviewNote(
        id = "note-1",
        status = "open",
        kind = "bug",
        message = "Проверить привязку",
        location = NoteLocation(
            workspacePath = "sample.go",
            vcsRoot = null,
            vcsPath = null,
            head = null,
            fileSha256 = ReviewNoteAnchor.sha256(text),
            startOffset = offset,
            endOffset = offset + selection.length,
            startLine = 2,
            endLine = 2,
        ),
        anchor = NoteAnchor(
            selection = selection,
            prefix = prefix,
            suffix = suffix,
            symbol = null,
        ),
        createdAt = "2026-08-22T00:00:00Z",
    )
}
