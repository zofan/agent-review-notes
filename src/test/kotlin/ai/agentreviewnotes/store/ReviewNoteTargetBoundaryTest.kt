package ai.agentreviewnotes.store

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewNoteTargetBoundaryTest {
    @Test
    fun `обычный файл внутри проекта разрешается`() {
        val project = Files.createTempDirectory("review-target-project-")
        try {
            val file = Files.writeString(project.resolve("main.go"), "package main")
            assertEquals(file.toRealPath(), ReviewNoteTargetBoundary.resolve(project, file))
        } finally {
            project.toFile().deleteRecursively()
        }
    }

    @Test
    fun `final symlink отвергается`() {
        val project = Files.createTempDirectory("review-target-project-")
        val outside = Files.createTempFile("review-target-outside-", ".go")
        try {
            val link = project.resolve("link.go")
            Files.createSymbolicLink(link, outside)
            assertFailsWith<IllegalArgumentException> { ReviewNoteTargetBoundary.resolve(project, link) }
        } finally {
            project.toFile().deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `target под symlink parent отвергается`() {
        val project = Files.createTempDirectory("review-target-project-")
        val outside = Files.createTempDirectory("review-target-outside-")
        try {
            val file = Files.writeString(outside.resolve("main.go"), "package main")
            val link = project.resolve("linked")
            Files.createSymbolicLink(link, outside)
            assertFailsWith<IllegalArgumentException> {
                ReviewNoteTargetBoundary.resolve(project, link.resolve(file.fileName))
            }
        } finally {
            project.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}
