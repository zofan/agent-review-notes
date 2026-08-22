package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewStatus

internal data class ReviewNoteStatusChoice(
    val status: ReviewStatus,
    val title: String,
)

internal object ReviewNoteStatusChoices {
    val all = listOf(
        ReviewNoteStatusChoice(ReviewStatus.OPEN, "Open"),
        ReviewNoteStatusChoice(ReviewStatus.IN_PROGRESS, "In Progress"),
        ReviewNoteStatusChoice(ReviewStatus.RESOLVED, "Resolved"),
        ReviewNoteStatusChoice(ReviewStatus.WONT_FIX, "Won't Fix"),
        ReviewNoteStatusChoice(ReviewStatus.NEEDS_REANCHOR, "Needs Reanchor"),
        ReviewNoteStatusChoice(ReviewStatus.STALE, "Stale"),
    )
}
