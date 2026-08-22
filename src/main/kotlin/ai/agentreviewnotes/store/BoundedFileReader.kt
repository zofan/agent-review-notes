package ai.agentreviewnotes.store

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object BoundedFileReader {
    fun readUtf8(path: Path): String = decodeUtf8(readBytes(path))

    fun readBytes(path: Path): ByteArray {
        Files.newInputStream(path).use { input ->
            val content = input.readNBytes(ReviewNoteLimits.MAX_JSON_BYTES + 1)
            require(content.size <= ReviewNoteLimits.MAX_JSON_BYTES) { "Review note JSON слишком большой" }
            return content
        }
    }

    fun decodeUtf8(content: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(content)).toString() }
            .getOrElse { throw IllegalArgumentException("Review note содержит некорректный UTF-8", it) }
    }
}
