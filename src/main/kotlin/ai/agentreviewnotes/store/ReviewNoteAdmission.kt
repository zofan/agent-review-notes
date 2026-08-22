package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import java.nio.file.Path
import java.time.Instant

internal object ReviewNoteAdmission {
    private val noteId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val sha256 = Regex("[0-9a-f]{64}")
    private val reviewKinds = ReviewKind.entries.mapTo(HashSet(), ReviewKind::wireValue)
    private val reviewStatuses = ReviewStatus.entries.mapTo(HashSet(), ReviewStatus::wireValue)

    fun validate(note: ReviewNote): ReviewNote {
        val schema = requireNotNull(note.schema) { "Отсутствует версия схемы" }
        val id = requireNotNull(note.id) { "Отсутствует id заметки" }
        val kind = requireNotNull(note.kind) { "Отсутствует тип заметки" }
        val status = requireNotNull(note.status) { "Отсутствует статус заметки" }
        val message = requireNotNull(note.message) { "Отсутствует текст заметки" }
        val location = requireNotNull(note.location) { "Отсутствует location заметки" }
        val anchor = requireNotNull(note.anchor) { "Отсутствует anchor заметки" }
        val workspacePath = requireNotNull(location.workspacePath) { "Отсутствует путь заметки" }
        val fileSha256 = requireNotNull(location.fileSha256) { "Отсутствует hash файла" }
        requireNotNull(anchor.selection) { "Отсутствует anchor selection" }
        requireNotNull(anchor.prefix) { "Отсутствует anchor prefix" }
        requireNotNull(anchor.suffix) { "Отсутствует anchor suffix" }
        val createdAt = requireNotNull(note.createdAt) { "Отсутствует время создания" }

        require(schema == REVIEW_NOTE_SCHEMA) { "Неизвестная версия схемы" }
        require(isValidId(id)) { "Некорректный id заметки" }
        require(kind in reviewKinds) { "Некорректный тип заметки" }
        require(status in reviewStatuses) { "Некорректный статус заметки" }
        require(message.isNotBlank()) { "Пустой текст заметки" }
        require(fileSha256.matches(sha256)) { "Некорректный hash файла" }
        require(location.startOffset >= 0 && location.endOffset >= location.startOffset) {
            "Некорректный диапазон заметки"
        }
        require(location.startLine >= 1 && location.endLine >= location.startLine) {
            "Некорректные строки заметки"
        }
        require(isSafeWorkspacePath(workspacePath)) {
            "Путь заметки выходит за пределы проекта"
        }
        Instant.parse(createdAt)
        note.resolution?.let { resolution ->
            require(resolution.summary.isNotBlank()) { "Пустой resolution summary" }
            Instant.parse(resolution.resolvedAt)
            resolution.fileSha256?.let { hash ->
                require(hash.matches(sha256)) { "Некорректный resolution hash файла" }
            }
        }
        return note
    }

    fun requireValidId(id: String) {
        require(isValidId(id)) { "Некорректный id заметки" }
    }

    private fun isValidId(id: String): Boolean = noteId.matches(id)

    private fun isSafeWorkspacePath(value: String): Boolean {
        if (value.isBlank()) return false
        val path = runCatching { Path.of(value) }.getOrNull() ?: return false
        return !path.isAbsolute && path.normalize() == path && !path.startsWith("..")
    }
}
