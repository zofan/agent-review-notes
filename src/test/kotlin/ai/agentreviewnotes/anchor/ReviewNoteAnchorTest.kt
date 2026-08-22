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

        assertEquals("The selected fragment occurs more than once", result.reason)
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

        assertEquals("The selected fragment was not found", result.reason)
    }

    @Test
    fun `точный hash не принимает несовместимый сохраненный диапазон`() {
        val text = "alpha\nbeta\ngamma"
        val note = note(text, selection = "beta", offset = 6).copy(
            location = note(text, selection = "beta", offset = 6).location.copy(endOffset = 9),
        )

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, text))
    }

    @Test
    fun `контекстуальный матч не считается уникальным если есть 129 совпадений`() {
        val original = "left beta right"
        val note = note(original, selection = "beta", offset = 5, prefix = "left ", suffix = " right")
        val current = buildList {
            add(original)
            repeat(127) { add("other beta other") }
            add(original)
        }.joinToString("\n")

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, current))
    }

    @Test
    fun `перекрывающиеся совпадения считаются неоднозначными`() {
        val note = note("xaa", selection = "aa", offset = 1)

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, "aaa"))
    }

    @Test
    fun `лимит учитывает перекрывающиеся совпадения`() {
        val note = note("xaa", selection = "aa", offset = 1)

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(note, "a".repeat(130)))
    }

    @Test
    fun `точный snapshot принимает валидное пустое выделение`() {
        val text = "alpha"
        val result = ReviewNoteAnchor.resolve(note(text, selection = "", offset = 2), text)

        assertEquals(2, assertIs<AnchorResult.Resolved>(result).offset)
    }

    @Test
    fun `точный snapshot отвергает несовпадающий selection`() {
        val text = "alpha\nbeta\ngamma"
        val valid = note(text, selection = "beta", offset = 6)
        val corrupted = valid.copy(anchor = valid.anchor.copy(selection = "zeta"))

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(corrupted, text))
    }

    @Test
    fun `точный snapshot отвергает отрицательные и экстремальные offsets`() {
        val text = "alpha\nbeta\ngamma"
        val valid = note(text, selection = "beta", offset = 6)
        val negative = valid.copy(location = valid.location.copy(startOffset = -1, endOffset = 3))
        val extreme = valid.copy(location = valid.location.copy(startOffset = Int.MAX_VALUE, endOffset = Int.MAX_VALUE))

        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(negative, text))
        assertIs<AnchorResult.Unresolved>(ReviewNoteAnchor.resolve(extreme, text))
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
