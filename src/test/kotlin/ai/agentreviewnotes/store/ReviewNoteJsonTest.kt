package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.ReviewStatus
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewNoteJsonTest {
    @Test
    fun `смена статуса сохраняет неизвестные поля агента`() {
        val merged = ReviewNoteJson.mergeStatus(validJson(), NOTE_ID, ReviewStatus.RESOLVED)
        val root = JsonParser.parseString(merged).asJsonObject

        assertEquals("resolved", root.get("status").asString)
        assertEquals("keep-me", root.getAsJsonObject("agentExtension").get("value").asString)
    }

    @Test
    fun `несовпадение имени файла и id отклоняется`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(validJson(), "b23e4567-e89b-42d3-a456-426614174000")
        }
    }

    @Test
    fun `отсутствующий anchor отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.remove("anchor")

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `отсутствующий числовой offset отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.getAsJsonObject("location").remove("startOffset")

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `неполный resolution отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.add("resolution", JsonParser.parseString("""{"resolvedAt":"2026-08-22T01:00:00Z"}"""))

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `дробный offset отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.getAsJsonObject("location").addProperty("startOffset", 0.5)

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `offset за пределами Int отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.getAsJsonObject("location").add("startOffset", JsonParser.parseString("4294967296"))

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `дублированный contract key отклоняется`() {
        val duplicate = validJson().replace(
            "\"id\": \"$NOTE_ID\",",
            "\"id\": \"$NOTE_ID\",\n  \"id\": \"$NOTE_ID\",",
        )

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(duplicate, NOTE_ID)
        }
    }

    @Test
    fun `нестрогий boolean literal отклоняется`() {
        val invalid = validJson().replace(
            "\"agentExtension\": {",
            "\"invalidExtension\": TRUE,\n  \"agentExtension\": {",
        )

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(invalid, NOTE_ID)
        }
    }

    @Test
    fun `writer отклоняет заметку больше лимита`() {
        val note = ReviewNoteJson.decode(validJson(), NOTE_ID)

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.encode(note.copy(message = "x".repeat(ReviewNoteLimits.MAX_JSON_BYTES)))
        }
    }

    @Test
    fun `branch неверного типа отклоняется`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.getAsJsonObject("location").addProperty("branch", 42)

        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.decode(root.toString(), NOTE_ID)
        }
    }

    @Test
    fun `редактирование сохраняет неизвестные поля`() {
        val root = JsonParser.parseString(validJson()).asJsonObject
        root.addProperty("futureField", "preserve-me")
        root.getAsJsonObject("anchor").addProperty("futureAnchor", true)

        val updated = ReviewNoteJson.mergeEditable(
            content = root.toString(),
            expectedId = NOTE_ID,
            kind = "suggestion",
            message = "Новый текст",
        )
        val updatedRoot = JsonParser.parseString(updated).asJsonObject

        assertEquals("suggestion", updatedRoot.get("kind").asString)
        assertEquals("Новый текст", updatedRoot.get("message").asString)
        assertEquals("preserve-me", updatedRoot.get("futureField").asString)
        assertEquals(true, updatedRoot.getAsJsonObject("anchor").get("futureAnchor").asBoolean)
    }

    @Test
    fun `редактирование отклоняет пустой текст`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteJson.mergeEditable(validJson(), NOTE_ID, "bug", "  ")
        }
    }

    private fun validJson(): String =
        """
        {
          "schema": "agent.review.note.v1",
          "id": "$NOTE_ID",
          "status": "open",
          "kind": "bug",
          "message": "Проверить обновление",
          "location": {
            "workspacePath": "golang/sage/main.go",
            "vcsRoot": "golang/sage",
            "vcsPath": "main.go",
            "head": null,
            "fileSha256": "${"0".repeat(64)}",
            "startOffset": 0,
            "endOffset": 1,
            "startLine": 1,
            "endLine": 1
          },
          "anchor": {
            "selection": "x",
            "prefix": "",
            "suffix": "",
            "symbol": null
          },
          "createdAt": "2026-08-22T00:00:00Z",
          "resolution": null,
          "agentExtension": {
            "value": "keep-me"
          }
        }
        """.trimIndent()

    private companion object {
        const val NOTE_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
