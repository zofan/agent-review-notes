package ai.agentreviewnotes.marker

internal object ReviewNoteInlayText {
    fun fit(value: String, maxWidth: Int, measure: (String) -> Int): String {
        if (maxWidth <= 0 || value.isEmpty()) return ""
        if (measure(value) <= maxWidth) return value
        val ellipsis = "…"
        if (measure(ellipsis) > maxWidth) return ""
        var low = 0
        var high = value.length
        while (low < high) {
            val candidate = (low + high + 1) / 2
            if (measure(value.take(candidate) + ellipsis) <= maxWidth) {
                low = candidate
            } else {
                high = candidate - 1
            }
        }
        return value.take(low).trimEnd() + ellipsis
    }

    fun display(kindTitle: String, message: String, maxChars: Int = 120): String {
        val prefix = "$kindTitle · "
        val limit = maxOf(maxChars, prefix.length + 1)
        val normalized = message.trim().replace(Regex("\\s+"), " ")
        val full = prefix + normalized
        if (full.length <= limit) return full
        val messageLimit = limit - prefix.length
        if (messageLimit <= 1) return prefix + "…"
        return prefix + normalized.take(messageLimit - 1).trimEnd() + "…"
    }
}
