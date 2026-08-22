package ai.agentreviewnotes.store

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object ReviewNoteFileLock {
    private val localLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(directory: java.nio.file.Path, id: String, action: () -> T): T {
        ReviewNoteAdmission.requireValidId(id)
        val local = localLocks.computeIfAbsent(id) { ReentrantLock() }
        return local.withLock {
            val lockDirectory = requireNotNull(
                ReviewNoteStorageBoundary.resolve(directory, directory.resolve(".locks"), create = true),
            )
            val lockFile = lockDirectory.resolve("$id.lock")
            if (Files.exists(lockFile, NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(lockFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(lockFile)) {
                    "Файл блокировки заметки небезопасен"
                }
            }
            FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
                channel.lock().use { action() }
            }
        }
    }
}
