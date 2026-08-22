package ai.agentreviewnotes.store

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicReviewNoteReplaceTest {
    @Test
    fun `atomic replace заменяет target и переносит source`() {
        val directory = Files.createTempDirectory("review-note-replace-")
        try {
            val source = directory.resolve("source.tmp")
            val target = directory.resolve("target.json")
            Files.writeString(source, "new")
            Files.writeString(target, "old")

            AtomicReviewNoteReplace().replace(source, target)

            assertEquals("new", Files.readString(target))
            assertTrue(Files.notExists(source))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `unsupported atomic move не заменяет target обычным move`() {
        val directory = Files.createTempDirectory("review-note-replace-")
        try {
            val source = directory.resolve("source.tmp")
            val target = directory.resolve("target.json")
            Files.writeString(source, "new")
            Files.writeString(target, "old")
            val replace = AtomicReviewNoteReplace { from, to ->
                throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected")
            }

            assertFailsWith<AtomicMoveNotSupportedException> { replace.replace(source, target) }
            assertEquals("old", Files.readString(target))
            assertTrue(Files.exists(source))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
