package ai.agentreviewnotes.marker

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ReviewNoteRefreshGeneration {
    private val generations = ConcurrentHashMap<String, AtomicLong>()
    private val epoch = AtomicLong()

    fun next(key: String): Long = generations.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()

    fun currentEpoch(): Long = epoch.get()

    fun isEpochCurrent(capturedEpoch: Long): Boolean = epoch.get() == capturedEpoch

    fun invalidateAll(): Long = epoch.incrementAndGet()

    fun isCurrent(key: String, generation: Long): Boolean = generations[key]?.get() == generation

    fun isCurrent(key: String, generation: Long, capturedEpoch: Long): Boolean =
        epoch.get() == capturedEpoch && isCurrent(key, generation)
}
