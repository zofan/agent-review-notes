package ai.agentreviewnotes.store

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ConcurrentModificationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.extension

@Service(Service.Level.PROJECT)
class ReviewNoteStore(private val project: Project) {
    private val log = logger<ReviewNoteStore>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val ioLock = ReentrantLock()
    private val pendingStatuses = ConcurrentHashMap.newKeySet<String>()
    private val executor = AppExecutorUtil.getAppExecutorService()
    private val atomicDelete = AtomicReviewNoteDelete()

    @Volatile
    private var snapshot: List<ReviewNote> = emptyList()

    fun cachedList(): List<ReviewNote> = snapshot

    fun refreshAsync(): CompletableFuture<List<ReviewNote>> =
        CompletableFuture.supplyAsync({ ioLock.withLock(::loadAndCache) }, executor)
            .whenComplete { _, error -> completeOperation(error) }

    fun createAsync(note: ReviewNote): CompletableFuture<Void> =
        CompletableFuture.runAsync(
            {
                ioLock.withLock {
                    writeNew(note)
                    loadAndCache()
                }
            },
            executor,
        ).whenComplete { _, error -> completeOperation(error) }

    fun setStatusAsync(id: String, status: ReviewStatus): CompletableFuture<Void> {
        val operation = "$id:${status.wireValue}"
        if (!pendingStatuses.add(operation)) return CompletableFuture.completedFuture(null)

        return CompletableFuture.runAsync(
            {
                ioLock.withLock {
                    mergeStatus(id, status)
                    loadAndCache()
                }
            },
            executor,
        ).whenComplete { _, error ->
            pendingStatuses.remove(operation)
            completeOperation(error)
        }
    }

    fun updateAsync(id: String, kind: ReviewKind, message: String): CompletableFuture<Void> =
        mutateAsync {
            mergeFile(id) { content ->
                ReviewNoteJson.mergeEditable(content, id, kind.wireValue, message)
            }
        }

    fun deleteAsync(id: String): CompletableFuture<Void> = mutateAsync {
        val directory = requireNotNull(notesDirectory(create = false)) { "Каталог заметок не существует" }
        val target = notePath(directory, id)
        val leftover = atomicDelete.delete(target)
        if (leftover != null) log.warn("Заметка удалена, но карантин не очищен: $leftover")
    }

    fun addListener(parent: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    private fun loadAndCache(): List<ReviewNote> {
        val loaded = load()
        snapshot = loaded
        return loaded
    }

    private fun load(): List<ReviewNote> {
        val directory = notesDirectory(create = false) ?: return emptyList()

        return Files.newDirectoryStream(directory).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) && it.extension == "json" }
                .mapNotNull(::read)
                .sortedBy { it.createdAt }
                .toList()
        }
    }

    private fun read(path: Path): ReviewNote? {
        return runCatching {
            val expectedId = path.fileName.toString().removeSuffix(".json")
            ReviewNoteAdmission.requireValidId(expectedId)
            ReviewNoteJson.decode(BoundedFileReader.readUtf8(path), expectedId)
        }.onFailure { error ->
            log.warn("Некорректная review note: $path", error)
        }.getOrNull()
    }

    private fun writeNew(note: ReviewNote) {
        val content = ReviewNoteJson.encode(note)
        val directory = requireNotNull(notesDirectory(create = true))
        writeTemporary(directory, note.id, content).useAsTemporary { temporary ->
            moveAtomically(temporary, notePath(directory, note.id))
        }
    }

    private fun mergeStatus(id: String, status: ReviewStatus) {
        mergeFile(id) { content -> ReviewNoteJson.mergeStatus(content, id, status) }
    }

    private fun mergeFile(id: String, transform: (String) -> String) {
        val directory = requireNotNull(notesDirectory(create = false)) { "Каталог заметок не существует" }
        val target = notePath(directory, id)
        require(Files.isRegularFile(target, NOFOLLOW_LINKS)) { "Заметка $id не существует или небезопасна" }
        repeat(MAX_STATUS_RETRIES) {
            val original = BoundedFileReader.readBytes(target)
            val content = transform(BoundedFileReader.decodeUtf8(original))
            val temporary = writeTemporary(directory, id, content)
            try {
                val latest = BoundedFileReader.readBytes(target)
                if (!latest.contentEquals(original)) return@repeat
                moveAtomically(temporary, target)
                return
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        throw ConcurrentModificationException("Заметка $id одновременно изменяется внешним процессом")
    }

    private fun mutateAsync(mutation: () -> Unit): CompletableFuture<Void> =
        CompletableFuture.runAsync(
            {
                ioLock.withLock {
                    mutation()
                    loadAndCache()
                }
            },
            executor,
        ).whenComplete { _, error -> completeOperation(error) }

    private fun writeTemporary(directory: Path, id: String, content: String): Path {
        ReviewNoteAdmission.requireValidId(id)
        val temporary = Files.createTempFile(directory, id, ".tmp")
        Files.writeString(temporary, content, Charsets.UTF_8)
        return temporary
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun notePath(directory: Path, id: String): Path {
        ReviewNoteAdmission.requireValidId(id)
        return directory.resolve("$id.json")
    }

    private fun notesDirectory(create: Boolean): Path? {
        val basePath = requireNotNull(project.basePath) { "У проекта нет локального корневого каталога" }
        val root = Path.of(basePath)
        val requested = root.resolve(".idea/agent-review-notes/notes")
        return ReviewNoteStorageBoundary.resolve(root, requested, create)
    }

    private fun completeOperation(error: Throwable?) {
        if (error != null) {
            log.warn("Операция с review notes завершилась ошибкой", error)
            return
        }
        if (!project.isDisposed) listeners.forEach { it.invoke() }
    }

    private inline fun Path.useAsTemporary(block: (Path) -> Unit) {
        try {
            block(this)
        } finally {
            Files.deleteIfExists(this)
        }
    }

    private companion object {
        const val MAX_STATUS_RETRIES = 3
    }
}