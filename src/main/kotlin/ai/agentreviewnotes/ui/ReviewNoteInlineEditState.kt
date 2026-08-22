package ai.agentreviewnotes.ui

internal enum class ReviewNoteInlineField {
    TYPE,
    STATUS,
    NOTE,
    DELETE,
}

internal data class ReviewNoteInlineEditState(
    val activeField: ReviewNoteInlineField? = null,
    val pending: Boolean = false,
) {
    val canClose: Boolean
        get() = !pending

    fun begin(field: ReviewNoteInlineField): ReviewNoteInlineEditState =
        if (pending) this else ReviewNoteInlineEditState(activeField = field)

    fun saving(field: ReviewNoteInlineField): ReviewNoteInlineEditState {
        require(activeField == field && !pending)
        return copy(pending = true)
    }

    fun succeeded(): ReviewNoteInlineEditState {
        require(pending)
        return ReviewNoteInlineEditState()
    }

    fun failed(): ReviewNoteInlineEditState {
        require(pending)
        return copy(pending = false)
    }

    fun cancel(field: ReviewNoteInlineField): ReviewNoteInlineEditState =
        if (activeField == field && !pending) ReviewNoteInlineEditState() else this
}
