package ai.agentreviewnotes.marker

internal object ReviewNoteMarkerOffset {
    fun forDocument(offset: Int, textLength: Int): Int? = when {
        textLength <= 0 -> null
        offset < 0 || offset > textLength -> null
        offset == textLength -> textLength - 1
        else -> offset
    }

    fun containsCharacter(startOffset: Int, endOffset: Int, offset: Int): Boolean =
        offset >= startOffset && offset < endOffset
}
