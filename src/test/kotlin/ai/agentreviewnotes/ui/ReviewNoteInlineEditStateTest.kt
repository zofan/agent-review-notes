package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteInlineEditStateTest {
    @Test
    fun `one edit action exposes the complete form and pending save blocks closing`() {
        val editing = ReviewNoteInlineEditState().beginEditing()
        assertTrue(editing.editing)
        assertFalse(editing.pending)

        val saving = editing.saving()
        assertTrue(saving.editing)
        assertTrue(saving.pending)
        assertFalse(saving.canClose)
        assertEquals(saving, saving.beginEditing())
        assertEquals(saving, saving.cancel())
    }

    @Test
    fun `successful save returns to view while failure keeps complete form editable`() {
        val saving = ReviewNoteInlineEditState().beginEditing().saving()

        assertEquals(ReviewNoteInlineEditState(), saving.succeeded())
        assertEquals(
            ReviewNoteInlineEditState(editing = true),
            saving.failed(),
        )
    }

    @Test
    fun `cancel returns a non-pending edit to view`() {
        assertEquals(
            ReviewNoteInlineEditState(),
            ReviewNoteInlineEditState().beginEditing().cancel(),
        )
    }

    @Test
    fun `untouched persisted whitespace is preserved by save preparation`() {
        assertEquals("  keep exact text  ", reviewNoteMessageForSave("  keep exact text  ", "  keep exact text  "))
        assertEquals("changed text", reviewNoteMessageForSave("old", "  changed text  "))
    }

    @Test
    fun `delete can enter pending state without edit mode`() {
        val deleting = ReviewNoteInlineEditState().saving()
        assertFalse(deleting.editing)
        assertTrue(deleting.pending)
        assertEquals(ReviewNoteInlineEditState(), deleting.failed())
    }
}
