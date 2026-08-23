package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteListPresentationTest {
    @Test
    fun `row contains only requested compact fields and truncates message`() {
        val message = "  A long review note   with whitespace " + "x".repeat(140) + " 😀 tail"
        val note = note(message = message)

        val row = ReviewNoteListPresentation.row(note)

        assertEquals("Bug · in progress · feature/symlinks · main.go:27", row.prefix)
        assertTrue(row.messagePreview.endsWith("…"))
        assertTrue(row.messagePreview.codePointCount(0, row.messagePreview.length) <= 100)
        assertEquals("${row.prefix} — ${row.messagePreview}", row.text)
        assertEquals(message, row.toolTip)
        assertFalse(row.text.contains("golang/handler/main.go"))
        assertFalse(row.text.contains("HandleMessage"))
        assertFalse(row.text.contains("tail"))
    }

    @Test
    fun `preview truncation preserves complete unicode code points`() {
        val message = "a".repeat(99) + "😀" + "tail"

        val preview = ReviewNoteListPresentation.messagePreview(message)

        assertEquals(100, preview.codePointCount(0, preview.length))
        assertTrue(preview.endsWith("…"))
        assertEquals("a".repeat(99) + "…", preview)
    }

    @Test
    fun `directory row uses directory name without fake line`() {
        val row = ReviewNoteListPresentation.row(
            note(path = "golang/handler", line = 0, target = "directory", branch = null, message = "Directory note"),
        )

        assertEquals("Bug · in progress · — · handler/", row.prefix)
    }

    private fun note(
        path: String = "golang/handler/main.go",
        line: Int = 27,
        target: String? = null,
        branch: String? = "feature/symlinks",
        message: String,
    ) = ReviewNote(
        id = "note-1",
        status = "in_progress",
        kind = "bug",
        message = message,
        location = NoteLocation(
            workspacePath = path,
            vcsRoot = "golang/handler",
            vcsPath = "main.go",
            head = "abc123",
            fileSha256 = "a".repeat(64),
            startOffset = 0,
            endOffset = 1,
            startLine = line,
            endLine = line,
            branch = branch,
            target = target,
        ),
        anchor = NoteAnchor("x", "", "", "HandleMessage"),
        createdAt = "2026-08-23T00:00:00Z",
    )
}
