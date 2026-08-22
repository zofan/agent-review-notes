package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.store.ReviewNoteAdmission
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryReviewNoteFactoryTest {
    @Test
    fun `factory создает admitted заметку на каталог`() {
        val note = DirectoryReviewNoteFactory.create(
            workspacePath = "services/api",
            vcsRoot = "services/api",
            vcsPath = "",
            head = "abc123",
            branch = "main",
            kind = ReviewKind.SUGGESTION,
            message = "Переименовать каталог",
            id = "123e4567-e89b-42d3-a456-426614174000",
            createdAt = "2026-08-22T00:00:00Z",
        )

        assertEquals(note, ReviewNoteAdmission.validate(note))
        assertEquals("directory", note.location.target)
        assertEquals("", note.location.fileSha256)
        assertEquals("", note.anchor.selection)
    }

    @Test
    fun `Git metadata опускается если repository root выше project root`() {
        val location = DirectoryReviewNoteFactory.gitLocation(
            projectRoot = Path.of("/repo/project"),
            directory = Path.of("/repo/project/services/api"),
            repositoryRoot = Path.of("/repo"),
            head = "abc123",
            branch = "main",
        )

        assertEquals(DirectoryGitLocation(null, null, null, null), location)
    }
}
