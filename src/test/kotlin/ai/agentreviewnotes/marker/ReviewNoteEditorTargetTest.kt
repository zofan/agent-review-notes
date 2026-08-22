package ai.agentreviewnotes.marker

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteEditorTargetTest {
    @Test
    fun `selection matches every intersecting note with half-open boundaries`() {
        val spans = listOf(
            ReviewNoteEditorSpan("left", 0, 6, 100),
            ReviewNoteEditorSpan("right", 10, 20, 300),
            ReviewNoteEditorSpan("touching-end", 12, 14, 500),
        )

        assertEquals(
            listOf("right", "left"),
            ReviewNoteEditorTarget.matchingIds(spans, queryStart = 5, queryEnd = 12),
        )
    }

    @Test
    fun `caret point matches containing range and excludes shared end boundary`() {
        val spans = listOf(
            ReviewNoteEditorSpan("ending", 2, 8, 500),
            ReviewNoteEditorSpan("containing", 8, 16, 200),
        )

        assertEquals(
            listOf("containing"),
            ReviewNoteEditorTarget.matchingIds(spans, queryStart = 8, queryEnd = 8),
        )
    }

    @Test
    fun `line query includes non-final line separator boundary`() {
        assertEquals(
            ReviewNoteEditorQuery(startOffset = 4, endOffset = 9),
            ReviewNoteEditorLineTarget.resolve(lineStart = 4, lineEnd = 8, textLength = 20),
        )
        assertEquals(
            ReviewNoteEditorQuery(startOffset = 12, endOffset = 20),
            ReviewNoteEditorLineTarget.resolve(lineStart = 12, lineEnd = 20, textLength = 20),
        )
    }

    @Test
    fun `line-end point note is matched by its non-final caret line query`() {
        val query = ReviewNoteEditorLineTarget.resolve(lineStart = 0, lineEnd = 8, textLength = 20)

        assertEquals(
            listOf("line-end"),
            ReviewNoteEditorTarget.matchingIds(
                spans = listOf(ReviewNoteEditorSpan("line-end", 8, 9, 100)),
                queryStart = query.startOffset,
                queryEnd = query.endOffset,
            ),
        )
    }

    @Test
    fun `caret at EOF on a non-empty final line includes the line and logical EOF point`() {
        val query = ReviewNoteEditorLineTarget.resolve(
            lineStart = 4,
            lineEnd = 8,
            textLength = 8,
            caretOffset = 8,
        )

        assertEquals(
            ReviewNoteEditorQuery(startOffset = 4, endOffset = 8, includeEndPoint = true),
            query,
        )
        assertEquals(
            ReviewNoteEditorQuery(startOffset = 4, endOffset = 8),
            ReviewNoteEditorLineTarget.resolve(
                lineStart = 4,
                lineEnd = 8,
                textLength = 8,
                caretOffset = 6,
            ),
        )
        assertEquals(
            listOf("line-note", "eof"),
            ReviewNoteEditorTarget.matchingIds(
                spans = listOf(
                    ReviewNoteEditorSpan("eof", 8, 8, 100),
                    ReviewNoteEditorSpan("line-note", 5, 7, 200),
                ),
                queryStart = query.startOffset,
                queryEnd = query.endOffset,
                includeEndPoint = query.includeEndPoint,
            ),
        )
    }

    @Test
    fun `logical point metadata distinguishes EOF fallback from normal anchors`() {
        assertEquals(
            true,
            ReviewNoteEditorLogicalTarget.pointAtEnd(
                anchorOffset = 8,
                selectionLength = 0,
                visualRange = ReviewNoteHighlightRange(7, 8),
            ),
        )
        assertEquals(
            false,
            ReviewNoteEditorLogicalTarget.pointAtEnd(
                anchorOffset = 4,
                selectionLength = 0,
                visualRange = ReviewNoteHighlightRange(4, 5),
            ),
        )
        assertEquals(
            null,
            ReviewNoteEditorLogicalTarget.pointAtEnd(
                anchorOffset = 4,
                selectionLength = 3,
                visualRange = ReviewNoteHighlightRange(4, 7),
            ),
        )
    }

    @Test
    fun `empty final line point target remains reachable at EOF`() {
        val textLength = 8
        val visual = ReviewNoteHighlightRange.resolve(
            offset = textLength,
            selectionLength = 0,
            textLength = textLength,
        )
        val query = ReviewNoteEditorLineTarget.resolve(
            lineStart = textLength,
            lineEnd = textLength,
            textLength = textLength,
        )

        assertEquals(ReviewNoteHighlightRange(7, 8), visual)
        assertEquals(
            listOf("eof"),
            ReviewNoteEditorTarget.matchingIds(
                spans = listOf(ReviewNoteEditorSpan("eof", textLength, textLength, 100)),
                queryStart = query.startOffset,
                queryEnd = query.endOffset,
            ),
        )
    }

    @Test
    fun `empty document logical point remains decorated and reachable`() {
        val visual = ReviewNoteHighlightRange.resolve(
            offset = 0,
            selectionLength = 0,
            textLength = 0,
        )!!
        val query = ReviewNoteEditorLineTarget.resolve(
            lineStart = 0,
            lineEnd = 0,
            textLength = 0,
            caretOffset = 0,
        )

        assertEquals(ReviewNoteHighlightRange(0, 0), visual)
        assertEquals(true, ReviewNoteEditorLogicalTarget.pointAtEnd(0, 0, visual))
        assertEquals(
            listOf("empty-eof"),
            ReviewNoteEditorTarget.matchingIds(
                spans = listOf(ReviewNoteEditorSpan("empty-eof", 0, 0, 100)),
                queryStart = query.startOffset,
                queryEnd = query.endOffset,
                includeEndPoint = query.includeEndPoint,
            ),
        )
    }

    @Test
    fun `point targets preserve half-open adjacent line boundaries`() {
        val spans = listOf(
            ReviewNoteEditorSpan("previous-eol", 7, 7, 100),
            ReviewNoteEditorSpan("final-eof", 8, 8, 100),
        )

        assertEquals(
            listOf("previous-eol"),
            ReviewNoteEditorTarget.matchingIds(spans, queryStart = 0, queryEnd = 8),
        )
        assertEquals(
            listOf("final-eof"),
            ReviewNoteEditorTarget.matchingIds(spans, queryStart = 8, queryEnd = 8),
        )
    }

    @Test
    fun `overlaps are deterministic by priority then narrowest range and id`() {
        val spans = listOf(
            ReviewNoteEditorSpan("wide", 0, 20, 300),
            ReviewNoteEditorSpan("zeta", 4, 12, 300),
            ReviewNoteEditorSpan("alpha", 4, 12, 300),
            ReviewNoteEditorSpan("blocker", 0, 30, 500),
        )

        assertEquals(
            listOf("blocker", "alpha", "zeta", "wide"),
            ReviewNoteEditorTarget.matchingIds(spans, queryStart = 6, queryEnd = 7),
        )
    }
}
