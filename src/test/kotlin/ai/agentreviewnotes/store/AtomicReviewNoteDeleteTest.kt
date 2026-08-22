package ai.agentreviewnotes.store

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomicReviewNoteDeleteTest {
    @Test
    fun `заметка атомарно удаляется через карантин`() {
        withTemporaryDirectory { directory ->
            val target = Files.writeString(directory.resolve("note.json"), "note")

            val leftover = AtomicReviewNoteDelete().delete(target)

            assertNull(leftover)
            assertFalse(Files.exists(target))
            assertEquals(emptyList(), Files.list(directory).use { it.toList() })
        }
    }

    @Test
    fun `отсутствие atomic move сохраняет исходную заметку`() {
        withTemporaryDirectory { directory ->
            val target = Files.writeString(directory.resolve("note.json"), "note")
            val delete = AtomicReviewNoteDelete(moveFile = { source, destination ->
                throw AtomicMoveNotSupportedException(source.toString(), destination.toString(), "unsupported")
            })

            assertFailsWith<AtomicMoveNotSupportedException> { delete.delete(target) }
            assertTrue(Files.isRegularFile(target))
            assertEquals("note", Files.readString(target))
        }
    }

    @Test
    fun `ошибка очистки карантина не отменяет завершенное удаление`() {
        withTemporaryDirectory { directory ->
            val target = Files.writeString(directory.resolve("note.json"), "note")
            val delete = AtomicReviewNoteDelete(
                deleteFile = { path ->
                    if (Files.exists(target)) Files.delete(path) else throw IllegalStateException("cleanup failed")
                },
            )

            val leftover = assertNotNull(delete.delete(target))

            assertFalse(Files.exists(target))
            assertTrue(Files.isRegularFile(leftover))
            Files.delete(leftover)
        }
    }

    @Test
    fun `символьная ссылка не удаляется как заметка`() {
        withTemporaryDirectory { directory ->
            val outside = Files.writeString(directory.resolve("outside.json"), "outside")
            val target = directory.resolve("note.json")
            Files.createSymbolicLink(target, outside)

            assertFailsWith<IllegalArgumentException> { AtomicReviewNoteDelete().delete(target) }
            assertTrue(Files.isSymbolicLink(target))
            assertEquals("outside", Files.readString(outside))
        }
    }

    private fun withTemporaryDirectory(block: (Path) -> Unit) {
        val directory = createTempDirectory("review-note-delete-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
