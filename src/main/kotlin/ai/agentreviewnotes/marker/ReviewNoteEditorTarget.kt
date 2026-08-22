package ai.agentreviewnotes.marker

internal data class ReviewNoteEditorQuery(
    val startOffset: Int,
    val endOffset: Int,
    val includeEndPoint: Boolean = false,
)

internal object ReviewNoteEditorLineTarget {
    fun resolve(
        lineStart: Int,
        lineEnd: Int,
        textLength: Int,
        caretOffset: Int? = null,
    ): ReviewNoteEditorQuery {
        require(lineStart >= 0 && lineEnd >= lineStart && textLength >= lineEnd)
        require(caretOffset == null || caretOffset in lineStart..lineEnd)
        val includeEndPoint = caretOffset == textLength && lineStart < lineEnd
        val queryEnd = if (lineEnd < textLength) lineEnd + 1 else lineEnd
        return ReviewNoteEditorQuery(lineStart, queryEnd, includeEndPoint)
    }
}

internal object ReviewNoteEditorLogicalTarget {
    fun pointAtEnd(
        anchorOffset: Int,
        selectionLength: Int,
        visualRange: ReviewNoteHighlightRange,
    ): Boolean? {
        require(selectionLength >= 0)
        if (selectionLength > 0) return null
        require(anchorOffset == visualRange.startOffset || anchorOffset == visualRange.endOffset)
        return anchorOffset == visualRange.endOffset
    }
}

internal data class ReviewNoteEditorSpan(
    val noteId: String,
    val startOffset: Int,
    val endOffset: Int,
    val priority: Int,
)

internal object ReviewNoteEditorTarget {
    fun matchingIds(
        spans: Iterable<ReviewNoteEditorSpan>,
        queryStart: Int,
        queryEnd: Int,
        includeEndPoint: Boolean = false,
    ): List<String> {
        require(queryStart >= 0 && queryEnd >= queryStart)
        val isPoint = queryStart == queryEnd
        return spans.asSequence()
            .filter { span ->
                if (span.startOffset < 0 || span.endOffset < span.startOffset) return@filter false
                val spanIsPoint = span.startOffset == span.endOffset
                when {
                    isPoint && spanIsPoint -> span.startOffset == queryStart
                    isPoint -> span.startOffset <= queryStart && queryStart < span.endOffset
                    spanIsPoint ->
                        queryStart <= span.startOffset &&
                            (span.startOffset < queryEnd || includeEndPoint && span.startOffset == queryEnd)
                    else -> span.startOffset < queryEnd && queryStart < span.endOffset
                }
            }
            .sortedWith(
                compareByDescending<ReviewNoteEditorSpan> { it.priority }
                    .thenBy { it.endOffset - it.startOffset }
                    .thenBy { it.noteId },
            )
            .map(ReviewNoteEditorSpan::noteId)
            .distinct()
            .toList()
    }
}
