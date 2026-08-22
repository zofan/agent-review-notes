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
            ),
            ReviewNoteStatusChoices.mutable.map { it.status to it.title },
        )
    }
}
