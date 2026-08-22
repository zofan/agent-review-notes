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

    @Test
    fun `пустая Git ветка отклоняется`() {
        val note = validNote("main.go")
        val location = note.location.copy(vcsRoot = ".", branch = "")

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(note.copy(location = location))
        }
    }

    @Test
    fun `Git ветка без корня репозитория отклоняется`() {
        val note = validNote("main.go")
        val location = note.location.copy(branch = "feature/review")

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(note.copy(location = location))
        }
    }

    @Test
    fun `несогласованные workspace и Git пути отклоняются`() {
        val note = validNote("services/web/main.go")
        val location = note.location.copy(
            vcsRoot = "services/api",
            vcsPath = "main.go",
            branch = "main",
        )

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(note.copy(location = location))
        }
    }

    @Test
    fun `Git root с обходом проекта отклоняется`() {
        val note = validNote("main.go")
        val location = note.location.copy(
            vcsRoot = "../outside",
            vcsPath = "main.go",
            branch = "main",
        )

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(note.copy(location = location))
        }
    }

    @Test
    fun `Git root с повторным разделителем отклоняется`() {
        assertInvalidVcsRoot("services//api")
    }

    @Test
    fun `Git root с завершающим разделителем отклоняется`() {
        assertInvalidVcsRoot("services/api/")
    }

    private fun assertInvalidVcsRoot(vcsRoot: String) {
        val note = validNote("services/api/main.go")
        val location = note.location.copy(
            vcsRoot = vcsRoot,
            vcsPath = "main.go",
            branch = "main",
        )

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteAdmission.validate(note.copy(location = location))
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
