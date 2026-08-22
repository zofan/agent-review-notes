package ai.agentreviewnotes.store

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNotePathPolicyTest {
    @Test
    fun `symlink alias проекта дает путь относительно real root`() {
        val parent = Files.createTempDirectory("review-path-policy-")
        try {
            val project = Files.createDirectory(parent.resolve("project"))
            val source = Files.createDirectories(project.resolve("src"))
            val file = Files.writeString(source.resolve("main.go"), "package main")
            val alias = parent.resolve("project-alias")
            Files.createSymbolicLink(alias, project)

            assertEquals(
                "src/main.go",
                ReviewNotePathPolicy.relative(alias, alias.resolve("src/main.go")),
            )
            assertEquals(
                "src/main.go",
                ReviewNotePathPolicy.relative(alias, file),
            )
        } finally {
            parent.toFile().deleteRecursively()
        }
    }
}
