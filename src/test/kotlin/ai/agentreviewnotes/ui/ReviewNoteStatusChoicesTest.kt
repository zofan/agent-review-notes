package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteStatusChoicesTest {
    @Test
    fun `status chooser exposes every user-managed status with readable titles`() {
        assertEquals(
            listOf(
                ReviewStatus.OPEN to "Open",
                ReviewStatus.IN_PROGRESS to "In Progress",
                ReviewStatus.RESOLVED to "Resolved",
                ReviewStatus.WONT_FIX to "Won't Fix",
                ReviewStatus.NEEDS_REANCHOR to "Needs Reanchor",
                ReviewStatus.STALE to "Stale",
            ),
            ReviewNoteStatusChoices.all.map { it.status to it.title },
        )
    }
}
