package ai.agentreviewnotes.marker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewNoteHighlightRangeTest {
    @Test
    fun `selection highlights exact resolved fragment`() {
        assertEquals(ReviewNoteHighlightRange(4, 11), ReviewNoteHighlightRange.resolve(4, 7, 20))
    }

    @Test
    fun `caret note highlights one character`() {
        assertEquals(ReviewNoteHighlightRange(4, 5), ReviewNoteHighlightRange.resolve(4, 0, 20))
    }

    @Test
    fun `caret at eof highlights final character`() {
        assertEquals(ReviewNoteHighlightRange(19, 20), ReviewNoteHighlightRange.resolve(20, 0, 20))
    }

    @Test
    fun `empty document produces a zero-length logical highlight target`() {
        assertEquals(ReviewNoteHighlightRange(0, 0), ReviewNoteHighlightRange.resolve(0, 0, 0))
        assertNull(ReviewNoteHighlightRange.resolve(0, 1, 0))
        assertNull(ReviewNoteHighlightRange.resolve(-1, 1, 20))
        assertNull(ReviewNoteHighlightRange.resolve(18, 3, 20))
    }
}
