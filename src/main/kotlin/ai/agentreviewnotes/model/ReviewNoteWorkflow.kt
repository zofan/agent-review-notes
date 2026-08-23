package ai.agentreviewnotes.model

import java.util.Locale

object ReviewNoteWorkflow {
    const val MAX_TAGS = 32
    const val MAX_DEPENDENCIES = 32

    fun parseTags(value: String): List<String> {
        val tags = value.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.lowercase(Locale.ROOT) }
            .distinct()
            .sorted()
        require(tags.size <= MAX_TAGS) { "At most $MAX_TAGS tags are allowed" }
        require(tags.all(::isValidTag)) { "Tags may contain lowercase letters, digits, ':', '_' and '-'" }
        return tags
    }

    fun formatTags(tags: List<String>): String = tags.joinToString(", ")

    fun isValidTag(tag: String): Boolean = TAG.matches(tag)

    private val TAG = Regex("[a-z0-9](?:[a-z0-9:_-]{0,62}[a-z0-9])?")
}
