package ai.agentreviewnotes.marker

data class ReviewNoteHighlightRange(val startOffset: Int, val endOffset: Int) {
    companion object {
        fun resolve(offset: Int, selectionLength: Int, textLength: Int): ReviewNoteHighlightRange? {
            if (textLength <= 0 || offset < 0 || offset > textLength || selectionLength < 0) return null
            if (selectionLength > 0) {
                val endOffset = offset + selectionLength
                if (endOffset > textLength) return null
                return ReviewNoteHighlightRange(offset, endOffset)
            }

            val startOffset = if (offset == textLength) textLength - 1 else offset
            return ReviewNoteHighlightRange(startOffset, startOffset + 1)
        }
    }
}
