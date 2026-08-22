package ai.agentreviewnotes.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteKindFilterTest {
    @Test
    fun `без выбранного типа видны все заметки`() {
        ReviewKind.entries.forEach { kind ->
            assertTrue(ReviewNoteKindFilter.isVisible(kind.wireValue, selectedKind = null))
        }
    }

    @Test
    fun `выбранный тип скрывает остальные типы`() {
        assertTrue(ReviewNoteKindFilter.isVisible("bug", ReviewKind.BUG))
        assertFalse(ReviewNoteKindFilter.isVisible("question", ReviewKind.BUG))
    }
}
