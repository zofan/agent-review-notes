package ai.agentreviewnotes.anchor

import ai.agentreviewnotes.model.ReviewNote
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

sealed interface AnchorResult {
    data class Resolved(val offset: Int) : AnchorResult
    data class Unresolved(val reason: String) : AnchorResult
}

object ReviewNoteAnchor {
    fun resolve(note: ReviewNote, currentText: String): AnchorResult {
        return resolve(note, currentText, sha256(currentText))
    }

    fun resolve(note: ReviewNote, currentText: String, currentSha256: String): AnchorResult {
        if (currentSha256 == note.location.fileSha256) {
            return exactSnapshot(note, currentText)
        }

        val selection = note.anchor.selection
        if (selection.isEmpty()) {
            return AnchorResult.Unresolved("The original line changed and there is no exact selection")
        }

        val occurrences = occurrenceOffsets(currentText, selection, MAX_OCCURRENCES + 1)
        if (occurrences.size > MAX_OCCURRENCES) {
            return AnchorResult.Unresolved("The selected fragment occurs too many times")
        }
        val contextual = occurrences.filter { offset -> contextMatches(note, currentText, offset) }
        if (contextual.size == 1) return AnchorResult.Resolved(contextual.first())

        return when (occurrences.size) {
            0 -> AnchorResult.Unresolved("The selected fragment was not found")
            1 -> AnchorResult.Resolved(occurrences.first())
            else -> AnchorResult.Unresolved("The selected fragment occurs more than once")
        }
    }

    private fun contextMatches(note: ReviewNote, text: String, offset: Int): Boolean {
        val prefix = note.anchor.prefix
        val suffix = note.anchor.suffix
        val prefixStart = offset - prefix.length
        val suffixStart = offset + note.anchor.selection.length
        if (prefixStart < 0 || suffixStart + suffix.length > text.length) return false
        return text.regionMatches(prefixStart, prefix, 0, prefix.length) &&
            text.regionMatches(suffixStart, suffix, 0, suffix.length)
    }

    fun sha256(text: String): String {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun occurrenceOffsets(text: String, selection: String, limit: Int): List<Int> {
        val offsets = ArrayList<Int>(limit)
        var start = 0
        while (offsets.size < limit) {
            val offset = text.indexOf(selection, start)
            if (offset < 0) break
            offsets.add(offset)
            start = offset + 1
        }
        return offsets
    }

    private fun exactSnapshot(note: ReviewNote, text: String): AnchorResult {
        val start = note.location.startOffset
        val end = note.location.endOffset
        val selection = note.anchor.selection
        if (start < 0 || end < start || end > text.length || end - start != selection.length) {
            return AnchorResult.Unresolved("The stored range does not match the file snapshot")
        }
        if (!text.regionMatches(start, selection, 0, selection.length)) {
            return AnchorResult.Unresolved("The stored fragment does not match the file snapshot")
        }
        return AnchorResult.Resolved(start)
    }

    private const val MAX_OCCURRENCES = 128
}
