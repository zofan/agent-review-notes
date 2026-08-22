package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA
import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA_V2
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import java.nio.file.Path
import java.time.Instant

internal object ReviewNoteAdmission {
    private val noteId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val sha256 = Regex("[0-9a-f]{64}")
    private val v1ReviewKinds = ReviewKind.entries
        .filterNot { it == ReviewKind.FEATURE }
        .mapTo(HashSet(), ReviewKind::wireValue)
    private val v2ReviewKinds = ReviewKind.entries.mapTo(HashSet(), ReviewKind::wireValue)
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

        val allowedKinds = when (schema) {
            REVIEW_NOTE_SCHEMA -> v1ReviewKinds
            REVIEW_NOTE_SCHEMA_V2 -> v2ReviewKinds
            else -> throw IllegalArgumentException("Неизвестная версия схемы")
        }
        require(isValidId(id)) { "Некорректный id заметки" }
        require(kind in allowedKinds) { "Некорректный тип заметки" }
        require(status in reviewStatuses) { "Некорректный статус заметки" }
        require(message.isNotBlank()) { "Пустой текст заметки" }
        val isDirectory = location.target == "directory"
        require(location.target == null || isDirectory) { "Некорректная цель заметки" }
        if (isDirectory) {
            require(fileSha256.isEmpty()) { "Для каталога не должно быть hash файла" }
            require(location.startOffset == 0 && location.endOffset == 0) { "Для каталога не должно быть offsets" }
            require(location.startLine == 0 && location.endLine == 0) { "Для каталога не должно быть строк" }
            require(anchor.selection.isEmpty() && anchor.prefix.isEmpty() && anchor.suffix.isEmpty() && anchor.symbol == null) {
                "Для каталога не должно быть файлового anchor"
            }
        } else {
            require(fileSha256.matches(sha256)) { "Некорректный hash файла" }
            require(location.startOffset >= 0 && location.endOffset >= location.startOffset) {
                "Некорректный диапазон заметки"
            }
            require(location.startLine >= 1 && location.endLine >= location.startLine) {
                "Некорректные строки заметки"
            }
        }
        require(isSafeWorkspacePath(workspacePath)) {
            "Путь заметки выходит за пределы проекта"
        }
        validateVcsLocation(location.vcsRoot, location.vcsPath, workspacePath, isDirectory)
        location.branch?.let { branch ->
            require(branch.isNotBlank()) { "Пустая Git-ветка заметки" }
            require(location.vcsRoot != null) { "Для Git-ветки отсутствует vcsRoot" }
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
        val canonical = path.normalize().toString()
            .replace(java.io.File.separatorChar, '/')
        return !path.isAbsolute && !path.startsWith("..") && canonical == value
    }

    private fun validateVcsLocation(
        vcsRoot: String?,
        vcsPath: String?,
        workspacePath: String,
        isDirectory: Boolean,
    ) {
        if (vcsRoot == null && vcsPath == null) return
        require(vcsRoot != null && vcsPath != null) { "Неполная Git-location заметки" }
        require(vcsRoot.isEmpty() || isSafeWorkspacePath(vcsRoot)) { "Некорректный vcsRoot заметки" }
        require(isSafeWorkspacePath(vcsPath) || isDirectory && vcsPath.isEmpty()) { "Некорректный vcsPath заметки" }
        val reconstructed = runCatching {
            Path.of(vcsRoot).resolve(vcsPath).normalize().toString()
                .replace(java.io.File.separatorChar, '/')
        }.getOrNull()
        require(reconstructed == workspacePath) { "Git-location не соответствует пути заметки" }
    }
}
