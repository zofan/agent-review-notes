package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA
import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA_V2
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.math.BigInteger

internal object ReviewNoteJson {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun decode(content: String, expectedId: String? = null): ReviewNote {
        val root = StrictJsonParser.parseObject(content)
        val note = decode(root)
        if (expectedId != null) {
            require(note.id == expectedId) { "Имя файла и id заметки не совпадают" }
        }
        return note
    }

    fun encode(note: ReviewNote): String {
        ReviewNoteAdmission.validate(note)
        return requireWithinLimit(gson.toJson(note))
    }

    fun mergeStatus(content: String, expectedId: String, status: ReviewStatus): String {
        val root = StrictJsonParser.parseObject(content)
        val current = decode(root)
        require(current.id == expectedId) { "Имя файла и id заметки не совпадают" }
        root.addProperty("status", status.wireValue)
        decode(root)
        return requireWithinLimit(gson.toJson(root))
    }

    fun mergeEditable(content: String, expectedId: String, kind: String, message: String): String =
        mergeEditableFields(content, expectedId, kind, status = null, message)

    fun mergeEditable(
        content: String,
        expectedId: String,
        kind: String,
        status: ReviewStatus,
        message: String,
    ): String = mergeEditableFields(content, expectedId, kind, status, message)

    private fun mergeEditableFields(
        content: String,
        expectedId: String,
        kind: String,
        status: ReviewStatus?,
        message: String,
    ): String {
        val root = StrictJsonParser.parseObject(content)
        val current = decode(root)
        require(current.id == expectedId) { "Имя файла и id заметки не совпадают" }
        if (kind == ReviewKind.FEATURE.wireValue && current.schema == REVIEW_NOTE_SCHEMA) {
            root.addProperty("schema", REVIEW_NOTE_SCHEMA_V2)
        }
        root.addProperty("kind", kind)
        status?.let { root.addProperty("status", it.wireValue) }
        root.addProperty("message", message)
        decode(root)
        return requireWithinLimit(gson.toJson(root))
    }

    private fun decode(root: JsonObject): ReviewNote {
        validateShape(root)
        return ReviewNoteAdmission.validate(gson.fromJson(root, ReviewNote::class.java))
    }

    private fun validateShape(root: JsonObject) {
        listOf("schema", "id", "status", "kind", "message", "createdAt").forEach { name ->
            requireString(root, name)
        }

        val location = requireObject(root, "location")
        listOf("workspacePath", "fileSha256").forEach { name -> requireString(location, name) }
        listOf("vcsRoot", "vcsPath", "head", "branch", "target").forEach { name -> requireOptionalString(location, name) }
        listOf("startOffset", "endOffset", "startLine", "endLine").forEach { name ->
            requireInt(location, name)
        }

        val anchor = requireObject(root, "anchor")
        listOf("selection", "prefix", "suffix").forEach { name -> requireString(anchor, name) }
        requireOptionalString(anchor, "symbol")

        validateResolution(root)
    }

    private fun requireObject(root: JsonObject, name: String): JsonObject {
        val value = root.get(name)
        require(value != null && value.isJsonObject) { "Поле $name должно быть объектом" }
        return value.asJsonObject
    }

    private fun requireString(root: JsonObject, name: String) {
        val value = root.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Поле $name должно быть строкой"
        }
    }

    private fun requireOptionalString(root: JsonObject, name: String) {
        val value = root.get(name) ?: return
        require(value.isJsonNull || value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Поле $name должно быть строкой или null"
        }
    }

    private fun requireInt(root: JsonObject, name: String) {
        val value = root.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "Поле $name должно быть числом"
        }
        val text = value.asString
        require(INTEGER.matches(text)) { "Поле $name должно быть целым числом" }
        val number = runCatching { BigInteger(text) }
            .getOrElse { throw IllegalArgumentException("Некорректное целое поле $name", it) }
        require(number >= INT_MIN && number <= INT_MAX) { "Поле $name выходит за диапазон Int" }
    }

    private fun validateResolution(root: JsonObject) {
        val value = root.get("resolution") ?: return
        if (value.isJsonNull) return
        require(value.isJsonObject) { "Поле resolution должно быть объектом или null" }
        val resolution = value.asJsonObject
        requireString(resolution, "summary")
        requireString(resolution, "resolvedAt")
        requireOptionalString(resolution, "fileSha256")
    }

    private fun requireWithinLimit(content: String): String {
        require(content.length <= ReviewNoteLimits.MAX_JSON_CHARS) { "Review note JSON слишком большой" }
        require(content.toByteArray(Charsets.UTF_8).size <= ReviewNoteLimits.MAX_JSON_BYTES) {
            "Review note JSON слишком большой"
        }
        return content
    }

    private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    private val INT_MIN = BigInteger.valueOf(Int.MIN_VALUE.toLong())
    private val INT_MAX = BigInteger.valueOf(Int.MAX_VALUE.toLong())
}
