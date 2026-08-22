package ai.agentreviewnotes.model

import java.time.Instant

object ReviewNoteCreatedAtFilter {
    fun isVisible(createdAt: String, from: Instant?, to: Instant?): Boolean {
        val created = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return false
        return (from == null || created >= from) && (to == null || created <= to)
    }
}
