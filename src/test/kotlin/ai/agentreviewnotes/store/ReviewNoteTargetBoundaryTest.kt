package ai.agentreviewnotes.store

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewNoteTargetBoundaryTest {
    @Test
    fun `обычный файл внутри проекта разрешается без repository mapping`() {
        val project = Files.createTempDirectory("review-target-project-")
        try {
            val file = Files.writeString(project.resolve("main.go"), "package main")
            assertEquals(file.toRealPath(), ReviewNoteTargetBoundary.resolve(project, file))
        } finally {
            project.toFile().deleteRecursively()
        }
    }

    @Test
    fun `symlink каталога зарегистрированного репозитория разрешается как lexical target`() {
        withProjectedRepository { project, repository, link ->
            val file = Files.writeString(repository.resolve("main.go"), "package main")
            val projected = link.resolve(file.fileName)
            val mapping = ReviewNoteRepositoryMapping(link, repository)

            assertEquals(projected, ReviewNoteTargetBoundary.resolve(project, projected, listOf(mapping)))
        }
    }

    @Test
    fun `внешний symlink без repository mapping отвергается`() {
        withProjectedRepository { project, repository, link ->
            val projected = link.resolve(Files.writeString(repository.resolve("main.go"), "package main").fileName)

            assertFailsWith<IllegalArgumentException> {
                ReviewNoteTargetBoundary.resolve(project, projected)
            }
        }
    }

    @Test
    fun `вложенный symlink из зарегистрированного репозитория наружу отвергается`() {
        withProjectedRepository { project, repository, link ->
            val outside = Files.createTempDirectory("review-target-secret-")
            try {
                val secret = Files.writeString(outside.resolve("secret.txt"), "secret")
                Files.createSymbolicLink(repository.resolve("escape"), outside)
                val projected = link.resolve("escape").resolve(secret.fileName)

                assertFailsWith<IllegalArgumentException> {
                    ReviewNoteTargetBoundary.resolve(
                        project,
                        projected,
                        listOf(ReviewNoteRepositoryMapping(link, repository)),
                    )
                }
            } finally {
                outside.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `внутренний symlink зарегистрированного репозитория разрешается`() {
        withProjectedRepository { project, repository, link ->
            val source = Files.createDirectories(repository.resolve("source"))
            val file = Files.writeString(source.resolve("main.go"), "package main")
            Files.createSymbolicLink(repository.resolve("alias"), source)
            val projected = link.resolve("alias").resolve(file.fileName)

            assertEquals(
                projected,
                ReviewNoteTargetBoundary.resolve(
                    project,
                    projected,
                    listOf(ReviewNoteRepositoryMapping(link, repository)),
                ),
            )
        }
    }

    @Test
    fun `поддельный repository mapping с другим canonical root отвергается`() {
        withProjectedRepository { project, repository, link ->
            val otherRepository = Files.createTempDirectory("review-target-other-repo-")
            try {
                val file = Files.writeString(repository.resolve("main.go"), "package main")

                assertFailsWith<IllegalArgumentException> {
                    ReviewNoteTargetBoundary.resolve(
                        project,
                        link.resolve(file.fileName),
                        listOf(ReviewNoteRepositoryMapping(link, otherRepository)),
                    )
                }
            } finally {
                otherRepository.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `canonical navigation target остается привязан к проверенному repository после замены symlink`() {
        val project = Files.createTempDirectory("review-target-project-")
        val repository = Files.createTempDirectory("review-target-repository-")
        val replacement = Files.createTempDirectory("review-target-replacement-")
        try {
            val originalFile = Files.writeString(repository.resolve("main.go"), "package original")
            Files.writeString(replacement.resolve("main.go"), "package replacement")
            val link = project.resolve("handler")
            Files.createSymbolicLink(link, repository)

            val canonicalTarget = ReviewNoteTargetBoundary.resolveCanonical(
                project,
                link.resolve("main.go"),
                listOf(ReviewNoteRepositoryMapping(link, repository)),
            )
            Files.delete(link)
            Files.createSymbolicLink(link, replacement)

            assertEquals(originalFile.toRealPath(), canonicalTarget)
            assertEquals(replacement.resolve("main.go").toRealPath(), link.resolve("main.go").toRealPath())
        } finally {
            project.toFile().deleteRecursively()
            repository.toFile().deleteRecursively()
            replacement.toFile().deleteRecursively()
        }
    }

    @Test
    fun `lexical target вне workspace отвергается даже если он существует`() {
        val project = Files.createTempDirectory("review-target-project-")
        val outside = Files.createTempFile("review-target-outside-", ".go")
        try {
            assertFailsWith<IllegalArgumentException> {
                ReviewNoteTargetBoundary.resolve(project, outside)
            }
        } finally {
            project.toFile().deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    private fun withProjectedRepository(block: (java.nio.file.Path, java.nio.file.Path, java.nio.file.Path) -> Unit) {
        val project = Files.createTempDirectory("review-target-project-")
        val repository = Files.createTempDirectory("review-target-repository-")
        try {
            val link = project.resolve("handler")
            Files.createSymbolicLink(link, repository)
            block(project, repository, link)
        } finally {
            project.toFile().deleteRecursively()
            repository.toFile().deleteRecursively()
        }
    }
}
