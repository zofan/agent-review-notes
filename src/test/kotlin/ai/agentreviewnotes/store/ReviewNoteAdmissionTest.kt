package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReviewNoteAdmissionTest {
    @Test
    fun `родительский путь отклоняется`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(validNote("../outside.go"))
        }
    }

    @Test
    fun `абсолютный путь отклоняется`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(validNote("/tmp/outside.go"))
        }
    }

    @Test
    fun `id с обходом каталога отклоняется`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.requireValidId("../../outside")
        }
    }

    private fun validNote(workspacePath: String): ReviewNote = ReviewNote(
        id = "123e4567-e89b-42d3-a456-426614174000",
        status = "open",
        kind = "bug",
        message = "Проверить границу",
        location = NoteLocation(
            workspacePath = workspacePath,
            vcsRoot = null,
            vcsPath = null,
            head = null,
            fileSha256 = "0".repeat(64),
            startOffset = 0,
            endOffset = 1,
            startLine = 1,
            endLine = 1,
        ),
        anchor = NoteAnchor(
            selection = "x",
            prefix = "",
            suffix = "",
            symbol = null,
        ),
        createdAt = "2026-08-22T00:00:00Z",
    )
}
