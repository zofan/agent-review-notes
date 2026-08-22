package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    private val onUpdate: (ReviewKind, String) -> CompletableFuture<*>,
    private val onChangeStatus: (ReviewStatus) -> CompletableFuture<*>,
    private val onDelete: () -> CompletableFuture<*>,
) : DialogWrapper(project) {
    private var currentKind = ReviewKind.entries.firstOrNull { it.wireValue == note.kind } ?: ReviewKind.SUGGESTION
    private var currentStatus = ReviewStatus.entries.firstOrNull { it.wireValue == note.status } ?: ReviewStatus.OPEN
    private var currentMessage = note.message
    private var state = ReviewNoteInlineEditState()

    private val typeValue = JBLabel()
    private val typeEditButton = editButton("Edit type") { beginInlineEdit(ReviewNoteInlineField.TYPE) }
    private val typeBox = ComboBox(ReviewKind.entries.toTypedArray())
    private val typeCards = JPanel(CardLayout())

    private val statusValue = JBLabel()
    private val statusEditButton = editButton("Edit status") { beginInlineEdit(ReviewNoteInlineField.STATUS) }
    private val statusBox = ComboBox(ReviewNoteStatusChoices.mutable.toTypedArray())
    private val statusCards = JPanel(CardLayout())

    private val noteValue = noteArea(editable = false)
    private val noteEditButton = editButton("Edit note") { beginInlineEdit(ReviewNoteInlineField.NOTE) }
    private val noteEditor = noteArea(editable = true)
    private val noteError = JBLabel().apply { foreground = JBColor.RED }
    private val noteCards = JPanel(CardLayout())
    private val inlineMutationButtons = mutableListOf<JButton>()

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
        configureInlinePanels()
        init()
        refreshInlineState()
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        ReviewNoteDetails.rows(note)
            .filterNot { it.label == "Type" || it.label == "Status" || it.label == "Note" }
            .forEach { row ->
                val component = if (row.label == "Snippet") {
                    JBScrollPane(noteArea(editable = false).apply { text = row.value })
                        .apply { preferredSize = Dimension(620, 90) }
                } else {
                    JBLabel(row.value)
                }
                builder.addLabeledComponent("${row.label}:", component)
            }
        return builder
            .addLabeledComponent("Type:", typeCards)
            .addLabeledComponent("Status:", statusCards)
            .addLabeledComponentFillVertically("Note:", noteCards)
            .panel
    }

    override fun createActions(): Array<Action> = arrayOf(deleteAction, closeAction)

    override fun doCancelAction() {
        if (state.canClose) super.doCancelAction()
    }

    private fun configureInlinePanels() {
        typeCards.add(viewRow(typeValue, typeEditButton), VIEW_CARD)
        typeCards.add(
            editRow(
                editor = typeBox,
                save = { saveKind() },
                cancel = { cancelInlineEdit(ReviewNoteInlineField.TYPE) },
            ),
            EDIT_CARD,
        )
        statusCards.add(viewRow(statusValue, statusEditButton), VIEW_CARD)
        statusCards.add(
            editRow(
                editor = statusBox,
                save = { saveStatus() },
                cancel = { cancelInlineEdit(ReviewNoteInlineField.STATUS) },
            ),
            EDIT_CARD,
        )

        noteValue.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) beginInlineEdit(ReviewNoteInlineField.NOTE)
            }
        })
        val noteView = JPanel(BorderLayout(8, 0)).apply {
            add(JBScrollPane(noteValue).apply { preferredSize = Dimension(620, 90) }, BorderLayout.CENTER)
            add(noteEditButton, BorderLayout.EAST)
        }
        val noteActions = actionButtons(
            save = { saveMessage() },
            cancel = { cancelInlineEdit(ReviewNoteInlineField.NOTE) },
        )
        val noteEdit = JPanel(BorderLayout(0, 4)).apply {
            add(JBScrollPane(noteEditor).apply { preferredSize = Dimension(620, 110) }, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                add(noteError, BorderLayout.CENTER)
                add(noteActions, BorderLayout.EAST)
            }, BorderLayout.SOUTH)
        }
        installEditKeys(noteEditor, save = { saveMessage() }, cancel = { cancelInlineEdit(ReviewNoteInlineField.NOTE) })
        noteCards.add(noteView, VIEW_CARD)
        noteCards.add(noteEdit, EDIT_CARD)
    }

    private fun beginInlineEdit(field: ReviewNoteInlineField) {
        if (state.pending) return
        state = state.begin(field)
        when (field) {
            ReviewNoteInlineField.TYPE -> typeBox.selectedItem = currentKind
            ReviewNoteInlineField.STATUS -> statusBox.selectedItem = ReviewNoteStatusChoices.mutable
                .firstOrNull { it.status == currentStatus }
                ?: ReviewNoteStatusChoices.mutable.first()
            ReviewNoteInlineField.NOTE -> {
                noteEditor.text = currentMessage
                noteError.text = ""
            }
            ReviewNoteInlineField.DELETE -> return
        }
        refreshInlineState()
        preferredFocus(field)?.requestFocusInWindow()
    }

    private fun cancelInlineEdit(field: ReviewNoteInlineField) {
        state = state.cancel(field)
        noteError.text = ""
        refreshInlineState()
    }

    private fun saveKind() {
        val selected = typeBox.item
        if (selected == currentKind) {
            cancelInlineEdit(ReviewNoteInlineField.TYPE)
            return
        }
        performMutation(
            field = ReviewNoteInlineField.TYPE,
            operation = { onUpdate(selected, currentMessage) },
            failureTitle = "Failed to change the review note type",
        ) { currentKind = selected }
    }

    private fun saveStatus() {
        val selected = statusBox.item.status
        if (selected == currentStatus) {
            cancelInlineEdit(ReviewNoteInlineField.STATUS)
            return
        }
        performMutation(
            field = ReviewNoteInlineField.STATUS,
            operation = { onChangeStatus(selected) },
            failureTitle = "Failed to change the review note status",
        ) { currentStatus = selected }
    }

    private fun saveMessage() {
        val message = noteEditor.text.trim()
        if (message.isBlank()) {
            noteError.text = "Enter the review note text"
            noteEditor.requestFocusInWindow()
            return
        }
        if (message == currentMessage) {
            cancelInlineEdit(ReviewNoteInlineField.NOTE)
            return
        }
        noteError.text = ""
        performMutation(
            field = ReviewNoteInlineField.NOTE,
            operation = { onUpdate(currentKind, message) },
            failureTitle = "Failed to edit the review note",
        ) { currentMessage = message }
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
        state = state.begin(ReviewNoteInlineField.DELETE)
        performMutation(
            field = ReviewNoteInlineField.DELETE,
            operation = { onDelete() },
            failureTitle = "Failed to delete the review note",
        ) {
            close(OK_EXIT_CODE)
        }
    }

    private fun performMutation(
        field: ReviewNoteInlineField,
        operation: () -> CompletableFuture<*>,
        failureTitle: String,
        onSuccess: () -> Unit,
    ) {
        if (state.pending || state.activeField != field) return
        state = state.saving(field)
        setMutationControlsEnabled(false)
        val future: CompletableFuture<*> = try {
            operation()
        } catch (error: Throwable) {
            CompletableFuture.failedFuture<Any?>(error)
        }
        future.whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater {
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
            }
        }
    }

    private fun refreshInlineState() {
        typeValue.text = currentKind.title
        typeValue.icon = ReviewNotePresentations.forKind(currentKind).icon()
        statusValue.text = statusTitle(currentStatus)
        noteValue.text = currentMessage
        showCard(typeCards, state.activeField == ReviewNoteInlineField.TYPE)
        showCard(statusCards, state.activeField == ReviewNoteInlineField.STATUS)
        showCard(noteCards, state.activeField == ReviewNoteInlineField.NOTE)
        setMutationControlsEnabled(!state.pending)
    }

    private fun setMutationControlsEnabled(enabled: Boolean) {
        typeEditButton.isEnabled = enabled
        statusEditButton.isEnabled = enabled
        noteEditButton.isEnabled = enabled
        typeBox.isEnabled = enabled
        statusBox.isEnabled = enabled
        noteEditor.isEnabled = enabled
        inlineMutationButtons.forEach { it.isEnabled = enabled }
        deleteAction.isEnabled = enabled
        closeAction.isEnabled = enabled && state.canClose
    }

    private fun preferredFocus(field: ReviewNoteInlineField): JComponent? = when (field) {
        ReviewNoteInlineField.TYPE -> typeBox
        ReviewNoteInlineField.STATUS -> statusBox
        ReviewNoteInlineField.NOTE -> noteEditor
        ReviewNoteInlineField.DELETE -> null
    }

    private fun showCard(panel: JPanel, edit: Boolean) {
        (panel.layout as CardLayout).show(panel, if (edit) EDIT_CARD else VIEW_CARD)
        panel.revalidate()
        panel.repaint()
    }

    private fun editRow(editor: JComponent, save: () -> Unit, cancel: () -> Unit): JPanel =
        JPanel(BorderLayout(8, 0)).apply {
            add(editor, BorderLayout.CENTER)
            add(actionButtons(save, cancel), BorderLayout.EAST)
            installEditKeys(editor, save, cancel)
        }

    private fun viewRow(value: JComponent, edit: JButton): JPanel = JPanel(BorderLayout(8, 0)).apply {
        add(value, BorderLayout.CENTER)
        add(edit, BorderLayout.EAST)
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

    private fun editButton(accessibleName: String, action: () -> Unit): JButton = JButton("Edit").apply {
        toolTipText = accessibleName
        accessibleContext.accessibleName = accessibleName
        addActionListener { action() }
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
        if (!editable) {
            isFocusable = true
            toolTipText = "Double-click or use Edit to change the note"
        }
    }

    private fun statusTitle(status: ReviewStatus): String = ReviewNoteStatusChoices.mutable
        .firstOrNull { it.status == status }
        ?.title
        ?: status.wireValue.replace('_', ' ').replaceFirstChar { it.uppercase() }

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

    private companion object {
        const val VIEW_CARD = "view"
        const val EDIT_CARD = "edit"
        const val SAVE_KEY = "review.note.inline.save"
        const val CANCEL_KEY = "review.note.inline.cancel"
    }
}
