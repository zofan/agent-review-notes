package ai.agentreviewnotes.store

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReviewNoteStorageBoundaryTest {
    @Test
    fun `симлинк каталога заметок за пределы проекта отклоняется`() {
        val root = createTempDirectory("review-project-")
        val outside = createTempDirectory("review-outside-")
        try {
            val dataRoot = Files.createDirectories(root.resolve(".idea/agent-review-notes"))
            val notes = dataRoot.resolve("notes")
            Files.createSymbolicLink(notes, outside)

            assertFailsWith<IllegalArgumentException> {
                ReviewNoteStorageBoundary.resolve(root, notes, create = true)
            }
        } finally {
            deleteTree(root)
            deleteTree(outside)
        }
    }

    @Test
    fun `симлинк родительского каталога отклоняется`() {
        val root = createTempDirectory("review-project-")
        val outside = createTempDirectory("review-outside-")
        try {
            Files.createSymbolicLink(root.resolve(".idea"), outside)
            val notes = root.resolve(".idea/agent-review-notes/notes")

            assertFailsWith<IllegalArgumentException> {
                ReviewNoteStorageBoundary.resolve(root, notes, create = true)
            }
        } finally {
            deleteTree(root)
            deleteTree(outside)
        }
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}