package ai.agentreviewnotes.marker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewNoteInlayTextTest {
    @Test
    fun `inlay text collapses whitespace and prefixes the note kind`() {
        assertEquals(
            "Feature · Add batch processing without blocking the editor",
            ReviewNoteInlayText.display("Feature", "  Add batch\nprocessing   without blocking the editor  ", 80),
        )
    }

    @Test
    fun `inlay text is bounded and keeps an ellipsis`() {
        val value = ReviewNoteInlayText.display("Blocker", "x".repeat(200), 48)

        assertEquals(48, value.length)
        assertTrue(value.endsWith("…"))
    }

    @Test
    fun `paint fitting keeps a visible ellipsis within the available width`() {
        assertEquals(
            "abc…",
            ReviewNoteInlayText.fit("abcdef", maxWidth = 4) { it.length },
        )
        assertEquals("", ReviewNoteInlayText.fit("abcdef", maxWidth = 0) { it.length })
    }

    @Test
    fun `minimum bound still returns useful text`() {
        assertEquals("Bug · …", ReviewNoteInlayText.display("Bug", "broken", 7))
    }
}
