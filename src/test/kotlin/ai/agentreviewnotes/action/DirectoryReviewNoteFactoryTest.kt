package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.store.ReviewNoteAdmission
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryReviewNoteFactoryTest {
    @Test
    fun `feature factory создает schema v3 с workflow полями`() {
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
            tags = listOf("component:sage"),
            dependsOn = listOf("b23e4567-e89b-42d3-a456-426614174000"),
        )

        assertEquals("agent.review.note.v3", note.schema)
        assertEquals(listOf("component:sage"), note.tags)
        assertEquals(listOf("b23e4567-e89b-42d3-a456-426614174000"), note.dependsOn)
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
            tags = emptyList(),
            dependsOn = emptyList(),
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

    @Test
    fun `symlinked repository сохраняет workspace alias и пути owning repo`() {
        val parent = Files.createTempDirectory("review-git-location-")
        try {
            val project = Files.createDirectory(parent.resolve("workspace"))
            val repository = Files.createDirectory(parent.resolve("handler"))
            val bundle = Files.createDirectory(repository.resolve("telegram"))
            val golang = Files.createDirectory(project.resolve("golang"))
            val alias = golang.resolve("handler")
            Files.createSymbolicLink(alias, repository)

            val location = ReviewNoteGitLocationResolver.resolve(
                projectRoot = project,
                target = alias.resolve(bundle.fileName),
                repositoryRoot = repository,
                head = "def456",
                branch = "feature",
            )

            assertEquals(ReviewNoteGitLocation("golang/handler", "telegram", "def456", "feature"), location)
        } finally {
            parent.toFile().deleteRecursively()
        }
    }
}
