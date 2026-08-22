package ai.agentreviewnotes.ui

internal fun reviewNoteMessageForSave(currentMessage: String, editorText: String): String =
    if (editorText == currentMessage) currentMessage else editorText.trim()

internal data class ReviewNoteInlineEditState(
    val editing: Boolean = false,
    val pending: Boolean = false,
) {
    val canClose: Boolean
        get() = !pending

    fun beginEditing(): ReviewNoteInlineEditState =
        if (pending) this else copy(editing = true)

    fun saving(): ReviewNoteInlineEditState {
        require(!pending)
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

    fun cancel(): ReviewNoteInlineEditState =
        if (pending) this else ReviewNoteInlineEditState()
}
