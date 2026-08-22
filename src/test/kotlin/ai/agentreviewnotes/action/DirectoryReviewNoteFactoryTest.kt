package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.store.ReviewNoteAdmission
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryReviewNoteFactoryTest {
    @Test
    fun `feature factory создает schema v2`() {
        val note = DirectoryReviewNoteFactory.create(
            workspacePath = "services/api",
            vcsRoot = "services/api",
            vcsPath = "",
            head = "abc123",
            branch = "main",
            kind = ReviewKind.FEATURE,
            message = "Добавить новый endpoint",
            id = "123e4567-e89b-42d3-a456-426614174000",
            createdAt = "2026-08-22T00:00:00Z",
        )

        assertEquals("agent.review.note.v2", note.schema)
        assertEquals(note, ReviewNoteAdmission.validate(note))
    }

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
        val location = ReviewNoteGitLocationResolver.resolve(
            projectRoot = Path.of("/repo/project"),
            target = Path.of("/repo/project/services/api"),
            repositoryRoot = Path.of("/repo"),
            head = "abc123",
            branch = "main",
        )

        assertEquals(ReviewNoteGitLocation(null, null, null, null), location)
    }

    @Test
    fun `project-root repository получает канонический пустой vcsRoot`() {
        val location = ReviewNoteGitLocationResolver.resolve(
            projectRoot = Path.of("/repo/project"),
            target = Path.of("/repo/project/services/api"),
            repositoryRoot = Path.of("/repo/project"),
            head = "abc123",
            branch = "main",
        )

        assertEquals(ReviewNoteGitLocation("", "services/api", "abc123", "main"), location)
    }

    @Test
    fun `nested repository получает пути относительно проекта и repository`() {
        val location = ReviewNoteGitLocationResolver.resolve(
            projectRoot = Path.of("/repo/project"),
            target = Path.of("/repo/project/services/api"),
            repositoryRoot = Path.of("/repo/project/services"),
            head = "def456",
            branch = "feature",
        )

        assertEquals(ReviewNoteGitLocation("services", "api", "def456", "feature"), location)
    }
}
