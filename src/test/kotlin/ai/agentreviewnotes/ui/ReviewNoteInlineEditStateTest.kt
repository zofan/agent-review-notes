package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteInlineEditStateTest {
    @Test
    fun `only one field edits at a time and pending mutations cannot be replaced`() {
        val editingType = ReviewNoteInlineEditState().begin(ReviewNoteInlineField.TYPE)
        assertEquals(ReviewNoteInlineField.TYPE, editingType.activeField)

        val editingNote = editingType.begin(ReviewNoteInlineField.NOTE)
        assertEquals(ReviewNoteInlineField.NOTE, editingNote.activeField)

        val saving = editingNote.saving(ReviewNoteInlineField.NOTE)
        assertTrue(saving.pending)
        assertFalse(saving.canClose)
        assertEquals(saving, saving.begin(ReviewNoteInlineField.STATUS))
        assertEquals(saving, saving.cancel(ReviewNoteInlineField.NOTE))
    }

    @Test
    fun `success returns to view while failure keeps the field editable`() {
        val saving = ReviewNoteInlineEditState()
            .begin(ReviewNoteInlineField.STATUS)
            .saving(ReviewNoteInlineField.STATUS)

        assertEquals(ReviewNoteInlineEditState(), saving.succeeded())
        assertEquals(
            ReviewNoteInlineEditState(activeField = ReviewNoteInlineField.STATUS),
            saving.failed(),
        )
    }

    @Test
    fun `cancel only affects the active non-pending field`() {
        val editing = ReviewNoteInlineEditState().begin(ReviewNoteInlineField.TYPE)
        assertEquals(editing, editing.cancel(ReviewNoteInlineField.STATUS))
        assertEquals(ReviewNoteInlineEditState(), editing.cancel(ReviewNoteInlineField.TYPE))
    }
}
