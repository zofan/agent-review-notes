package ai.agentreviewnotes.model

object ReviewNoteStatusFilter {
    fun isVisible(status: String, selectedStatus: ReviewStatus?): Boolean =
        selectedStatus == null || status == selectedStatus.wireValue
}
