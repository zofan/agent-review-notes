package ai.agentreviewnotes.store

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteStoreLockTest {
    @Test
    fun `общая блокировка сериализует изменения графа`() {
        val directory = createTempDirectory("review-note-store-lock")
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val first = thread {
            ReviewNoteStoreLock.withLock(directory) {
                firstEntered.countDown()
                releaseFirst.await()
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
        val second = thread {
            ReviewNoteStoreLock.withLock(directory) { secondEntered.countDown() }
        }

        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
        releaseFirst.countDown()
        assertTrue(secondEntered.await(2, TimeUnit.SECONDS))
        first.join()
        second.join()
    }
}