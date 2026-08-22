package ai.agentreviewnotes.store

import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BoundedFileReaderTest {
    @Test
    fun `файл больше лимита отклоняется до полной загрузки`() {
        val path = createTempFile("review-note-", ".json")
        try {
            Files.newOutputStream(path).use { output ->
                val block = ByteArray(8192) { 'x'.code.toByte() }
                var remaining = ReviewNoteLimits.MAX_JSON_BYTES + 1
                while (remaining > 0) {
                    val count = minOf(block.size, remaining)
                    output.write(block, 0, count)
                    remaining -= count
                }
            }

            assertFailsWith<IllegalArgumentException> {
                BoundedFileReader.readUtf8(path)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}