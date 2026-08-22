package ai.agentreviewnotes.projectview

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNotePresenceTest {
    @Test
    fun `файл получает badge только для активной заметки с точным путем`() {
        assertTrue(ReviewNotePresence.hasActiveNote("src/main.go", false, listOf(note("src/main.go"))))
        assertFalse(ReviewNotePresence.hasActiveNote("src/other.go", false, listOf(note("src/main.go"))))
        assertFalse(
            ReviewNotePresence.hasActiveNote(
                "src/main.go",
                false,
                listOf(note("src/main.go", ReviewStatus.RESOLVED.wireValue)),
            ),
        )
    }

    @Test
    fun `каталог получает badge для заметки внутри но не для соседнего префикса`() {
        assertTrue(ReviewNotePresence.hasActiveNote("src", true, listOf(note("src/pkg/file.go"))))
        assertFalse(ReviewNotePresence.hasActiveNote("src", true, listOf(note("src-other/file.go"))))
    }

    private fun note(path: String, status: String = ReviewStatus.OPEN.wireValue) = ReviewNote(
        id = "note-1",
        status = status,
        kind = "bug",
        message = "message",
        location = NoteLocation(path, null, null, null, "0".repeat(64), 0, 0, 1, 1),
        anchor = NoteAnchor("", "", "", null),
        createdAt = "2026-08-22T10:00:00Z",
    )
}
