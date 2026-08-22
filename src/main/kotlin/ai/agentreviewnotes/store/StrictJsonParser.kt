package ai.agentreviewnotes.store

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

internal object StrictJsonParser {
    fun parseObject(content: String): JsonObject {
        require(content.length <= ReviewNoteLimits.MAX_JSON_CHARS) { "Review note JSON слишком большой" }
        try {
            JsonReader(StringReader(content)).use { reader ->
                reader.strictness = Strictness.STRICT
                val value = read(reader, depth = 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "После JSON обнаружены лишние данные" }
                require(value.isJsonObject) { "Review note должна быть JSON-объектом" }
                return value.asJsonObject
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Некорректный JSON review note", error)
        }
    }

    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= MAX_DEPTH) { "Review note JSON имеет слишком большую вложенность" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readObject(reader, depth)
            JsonToken.BEGIN_ARRAY -> readArray(reader, depth)
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> readNumber(reader)
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonNull.INSTANCE
            }
            else -> throw IllegalArgumentException("Некорректный JSON token: ${reader.peek()}")
        }
    }

    private fun readObject(reader: JsonReader, depth: Int): JsonObject {
        val result = JsonObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(!result.has(name)) { "Дублированное JSON-поле: $name" }
            result.add(name, read(reader, depth + 1))
        }
        reader.endObject()
        return result
    }

    private fun readArray(reader: JsonReader, depth: Int): JsonArray {
        val result = JsonArray()
        reader.beginArray()
        while (reader.hasNext()) result.add(read(reader, depth + 1))
        reader.endArray()
        return result
    }

    private fun readNumber(reader: JsonReader): JsonPrimitive {
        val value = reader.nextString()
        val number = runCatching { BigDecimal(value) }
            .getOrElse { throw IllegalArgumentException("Некорректное JSON-число", it) }
        return JsonPrimitive(number)
    }

    private const val MAX_DEPTH = 64
}
