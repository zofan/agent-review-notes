package ai.agentreviewnotes.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteStatusFilterTest {
    @Test
    fun `without selected status all notes are visible`() {
        ReviewStatus.entries.forEach { status ->
            assertTrue(ReviewNoteStatusFilter.isVisible(status.wireValue, selectedStatus = null))
        }
    }

    @Test
    fun `selected status hides notes with other statuses`() {
        assertTrue(ReviewNoteStatusFilter.isVisible("in_progress", ReviewStatus.IN_PROGRESS))
        assertFalse(ReviewNoteStatusFilter.isVisible("resolved", ReviewStatus.IN_PROGRESS))
    }
}
