package ai.agentreviewnotes.store

import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewNoteFileLockTest {
    @Test
    fun `python advisory lock excludes the plugin writer on posix`() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val directory = createTempDirectory("review-note-python-lock")
        val lockFile = directory.resolve("shared.lock")
        val process = ProcessBuilder(
            "python3",
            "-c",
            "import fcntl,sys,time; f=open(sys.argv[1],'a+'); fcntl.lockf(f,fcntl.LOCK_EX); " +
                "print('ready',flush=True); time.sleep(10)",
            lockFile.toString(),
        ).start()
        try {
            assertTrue(process.inputReader().readLine() == "ready")
            FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                val acquired = channel.tryLock()
                try {
                    assertNull(acquired)
                } finally {
                    acquired?.release()
                }
            }
        } finally {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `same note lock excludes a concurrent writer`() {
        val directory = createTempDirectory("review-note-file-lock")
        val executor = Executors.newFixedThreadPool(2)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        try {
            executor.submit {
                ReviewNoteFileLock.withLock(directory, "11111111-1111-4111-8111-111111111111") {
                    firstEntered.countDown()
                    releaseFirst.await()
                }
            }
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
            executor.submit {
                ReviewNoteFileLock.withLock(directory, "11111111-1111-4111-8111-111111111111") {
                    secondEntered.countDown()
                }
            }

            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            directory.toFile().deleteRecursively()
        }
    }
}
