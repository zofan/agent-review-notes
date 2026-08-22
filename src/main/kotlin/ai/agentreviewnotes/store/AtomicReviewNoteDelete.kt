package ai.agentreviewnotes.store

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class AtomicReviewNoteDelete(
    private val deleteFile: (Path) -> Unit = Files::delete,
    private val moveFile: (Path, Path) -> Unit = { source, destination ->
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
    },
) {
    fun delete(target: Path): Path? {
        require(Files.isRegularFile(target, NOFOLLOW_LINKS)) {
            "Заметка не существует или небезопасна"
        }
        val directory = requireNotNull(target.parent) { "У заметки нет родительского каталога" }
        val quarantine = Files.createTempFile(directory, target.fileName.toString(), ".delete")
        try {
            deleteFile(quarantine)
            moveFile(target, quarantine)
        } catch (error: Exception) {
            Files.deleteIfExists(quarantine)
            throw error
        }
        return try {
            deleteFile(quarantine)
            null
        } catch (_: Exception) {
            quarantine
        }
    }
}
