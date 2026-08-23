package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteWorkflow
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke

internal class ReviewNoteDetailsDialog(
    private val project: Project,
    private val note: ReviewNote,
    private val availableParents: List<ReviewNote>,
    private val onSave: (ReviewKind, ReviewStatus, String, List<String>, List<String>) -> CompletableFuture<*>,
    private val onDelete: () -> CompletableFuture<*>,
) : DialogWrapper(project) {
    private var currentKind = ReviewKind.entries.firstOrNull { it.wireValue == note.kind } ?: ReviewKind.SUGGESTION
    private var currentStatus = ReviewStatus.entries.firstOrNull { it.wireValue == note.status } ?: ReviewStatus.OPEN
    private var currentMessage = note.message
    private var currentTags = note.tags
    private var currentDependsOn = note.dependsOn
    private var state = ReviewNoteInlineEditState()

    private val typeValue = JBLabel()
    private val statusValue = JBLabel()
    private val noteValue = noteArea(editable = false)
    private val tagsValue = JBLabel()
    private val dependsOnValue = JBLabel()
    private val typeBox = ComboBox(ReviewKind.entries.toTypedArray())
    private val statusBox = ComboBox(ReviewNoteStatusChoices.all.toTypedArray())
    private val noteEditor = noteArea(editable = true)
    private val tagsEditor = JBTextField()
    private val parentList = JBList(availableParents)
    private val noteError = JBLabel().apply { foreground = JBColor.RED }
    private val editCards = JPanel(CardLayout())
    private val inlineMutationButtons = mutableListOf<JButton>()
    private val editButton = JButton("Edit").apply {
        toolTipText = "Edit review note"
        accessibleContext.accessibleName = "Edit review note"
        addActionListener { beginEditing() }
    }

    private val deleteAction = object : AbstractAction("Delete…") {
        override fun actionPerformed(event: ActionEvent?) = deleteNote()
    }
    private val closeAction = object : AbstractAction("Close") {
        override fun actionPerformed(event: ActionEvent?) = doCancelAction()
    }

    init {
        title = "Review Note Details"
        typeBox.renderer = ReviewKindRenderer()
        statusBox.renderer = StatusRenderer()
        parentList.cellRenderer = ParentRenderer()
        parentList.visibleRowCount = 4
        configureEditCards()
        init()
        refreshInlineState()
    }

    override fun createCenterPanel(): JComponent {
        val metadata = FormBuilder.createFormBuilder()
        ReviewNoteDetails.rows(note)
            .filterNot { it.label in setOf("Type", "Status", "Tags", "Depends on", "Note") }
            .forEach { row ->
                val component = if (row.label == "Snippet") {
                    JBScrollPane(noteArea(editable = false).apply { text = row.value })
                        .apply { preferredSize = Dimension(620, 90) }
                } else {
                    JBLabel(row.value)
                }
                metadata.addLabeledComponent("${row.label}:", component)
            }
        return JPanel(BorderLayout(0, 10)).apply {
            add(metadata.panel, BorderLayout.NORTH)
            add(editCards, BorderLayout.CENTER)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(deleteAction, closeAction)

    override fun doCancelAction() {
        if (state.canClose) super.doCancelAction()
    }

    private fun configureEditCards() {
        val viewActions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { add(editButton) }
        val viewPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Type:", typeValue)
            .addLabeledComponent("Status:", statusValue)
            .addLabeledComponent("Tags:", tagsValue)
            .addLabeledComponent("Depends on:", dependsOnValue)
            .addLabeledComponentFillVertically(
                "Note:",
                JBScrollPane(noteValue).apply { preferredSize = Dimension(620, 90) },
            )
            .addComponent(viewActions)
            .panel

        val editActions = actionButtons(save = ::saveChanges, cancel = ::cancelEditing)
        val editPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Type:", typeBox)
            .addLabeledComponent("Status:", statusBox)
            .addLabeledComponent("Tags:", tagsEditor)
            .addLabeledComponentFillVertically(
                "Depends on:",
                JBScrollPane(parentList).apply { preferredSize = Dimension(620, 80) },
            )
            .addLabeledComponentFillVertically(
                "Note:",
                JBScrollPane(noteEditor).apply { preferredSize = Dimension(620, 110) },
            )
            .addComponent(JPanel(BorderLayout()).apply {
                add(noteError, BorderLayout.CENTER)
                add(editActions, BorderLayout.EAST)
            })
            .panel
        installEditKeys(editPanel, save = ::saveChanges, cancel = ::cancelEditing)

        editCards.add(viewPanel, VIEW_CARD)
        editCards.add(editPanel, EDIT_CARD)
    }

    private fun beginEditing() {
        if (state.pending) return
        state = state.beginEditing()
        typeBox.selectedItem = currentKind
        statusBox.selectedItem = ReviewNoteStatusChoices.all
            .firstOrNull { it.status == currentStatus }
            ?: ReviewNoteStatusChoices.all.first()
        noteEditor.text = currentMessage
        tagsEditor.text = ReviewNoteWorkflow.formatTags(currentTags)
        val selected = availableParents.mapIndexedNotNull { index, parent ->
            index.takeIf { parent.id in currentDependsOn }
        }
        parentList.selectedIndices = selected.toIntArray()
        noteError.text = ""
        refreshInlineState()
        typeBox.requestFocusInWindow()
    }

    private fun cancelEditing() {
        state = state.cancel()
        noteError.text = ""
        refreshInlineState()
    }

    private fun saveChanges() {
        if (!state.editing || state.pending) return
        val selectedKind = typeBox.item
        val selectedStatus = statusBox.item.status
        val message = reviewNoteMessageForSave(currentMessage, noteEditor.text)
        val tags = runCatching { ReviewNoteWorkflow.parseTags(tagsEditor.text) }.getOrElse { error ->
            noteError.text = error.message ?: "Invalid tags"
            tagsEditor.requestFocusInWindow()
            return
        }
        val dependsOn = parentList.selectedValuesList.map(ReviewNote::id)
        if (dependsOn.size > ReviewNoteWorkflow.MAX_DEPENDENCIES) {
            noteError.text = "Select no more than ${ReviewNoteWorkflow.MAX_DEPENDENCIES} dependencies"
            parentList.requestFocusInWindow()
            return
        }
        if (message.isBlank()) {
            noteError.text = "Enter the review note text"
            noteEditor.requestFocusInWindow()
            return
        }
        if (
            selectedKind == currentKind && selectedStatus == currentStatus && message == currentMessage &&
            tags == currentTags && dependsOn == currentDependsOn
        ) {
            cancelEditing()
            return
        }
        noteError.text = ""
        performMutation(
            operation = { onSave(selectedKind, selectedStatus, message, tags, dependsOn) },
            failureTitle = "Failed to edit the review note",
        ) {
            currentKind = selectedKind
            currentStatus = selectedStatus
            currentMessage = message
            currentTags = tags
            currentDependsOn = dependsOn
        }
    }

    private fun deleteNote() {
        if (!state.canClose) return
        val answer = Messages.showYesNoDialog(
            project,
            "Delete this review note? This cannot be undone.",
            "Delete Review Note",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return
        performMutation(
            operation = { onDelete() },
            failureTitle = "Failed to delete the review note",
        ) {
            close(OK_EXIT_CODE)
        }
    }

    private fun performMutation(
        operation: () -> CompletableFuture<*>,
        failureTitle: String,
        onSuccess: () -> Unit,
    ) {
        if (state.pending) return
        state = state.saving()
        setMutationControlsEnabled(false)
        val completionModality = ModalityState.stateForComponent(rootPane)
        val future: CompletableFuture<*> = try {
            operation()
        } catch (error: Throwable) {
            CompletableFuture.failedFuture<Any?>(error)
        }
        future.whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed || !isShowing) return@invokeLater
                if (error == null) {
                    state = state.succeeded()
                    onSuccess()
                } else {
                    state = state.failed()
                    val message = error.cause?.message ?: error.message ?: "Unknown error"
                    Messages.showErrorDialog(project, message, failureTitle)
                }
                refreshInlineState()
            }, completionModality)
        }
    }

    private fun refreshInlineState() {
        typeValue.text = currentKind.title
        typeValue.icon = ReviewNotePresentations.forKind(currentKind).icon()
        statusValue.text = statusTitle(currentStatus)
        tagsValue.text = ReviewNoteWorkflow.formatTags(currentTags).ifBlank { "—" }
        dependsOnValue.text = currentDependsOn.joinToString().ifBlank { "—" }
        noteValue.text = currentMessage
        (editCards.layout as CardLayout).show(editCards, if (state.editing) EDIT_CARD else VIEW_CARD)
        editCards.revalidate()
        editCards.repaint()
        setMutationControlsEnabled(!state.pending)
    }

    private fun setMutationControlsEnabled(enabled: Boolean) {
        editButton.isEnabled = enabled
        typeBox.isEnabled = enabled
        statusBox.isEnabled = enabled
        noteEditor.isEnabled = enabled
        tagsEditor.isEnabled = enabled
        parentList.isEnabled = enabled
        inlineMutationButtons.forEach { it.isEnabled = enabled }
        deleteAction.isEnabled = enabled
        closeAction.isEnabled = enabled && state.canClose
    }

    private fun actionButtons(save: () -> Unit, cancel: () -> Unit): JPanel {
        val saveButton = JButton("Save").apply {
            toolTipText = "Save (Ctrl+Enter)"
            addActionListener { save() }
        }
        val cancelButton = JButton("Cancel").apply {
            toolTipText = "Cancel (Escape)"
            addActionListener { cancel() }
        }
        inlineMutationButtons += saveButton
        inlineMutationButtons += cancelButton
        return JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(saveButton)
            add(cancelButton)
        }
    }

    private fun installEditKeys(component: JComponent, save: () -> Unit, cancel: () -> Unit) {
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), SAVE_KEY)
        component.actionMap.put(SAVE_KEY, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = save()
        })
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL_KEY)
        component.actionMap.put(CANCEL_KEY, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = cancel()
        })
    }

    private fun noteArea(editable: Boolean): JBTextArea = JBTextArea(4, 56).apply {
        isEditable = editable
        lineWrap = true
        wrapStyleWord = true
    }

    private fun statusTitle(status: ReviewStatus): String = ReviewNoteStatusChoices.all
        .first { it.status == status }
        .title

    private class ReviewKindRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (component is JLabel && value is ReviewKind) {
                component.text = value.title
                component.icon = ReviewNotePresentations.forKind(value).icon()
            }
            return component
        }
    }

    private class StatusRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is ReviewNoteStatusChoice) text = value.title
            return this
        }
    }

    private class ParentRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is ReviewNote) text = "${value.kind} · ${value.location.workspacePath} — ${value.message.take(80)}"
            return this
        }
    }

    private companion object {
        const val VIEW_CARD = "view"
        const val EDIT_CARD = "edit"
        const val SAVE_KEY = "review.note.edit.save"
        const val CANCEL_KEY = "review.note.edit.cancel"
    }
}
