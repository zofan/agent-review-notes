package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteWorkflow
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class ReviewNoteDialog(
    project: Project,
    initialKind: ReviewKind? = null,
    initialMessage: String = "",
    availableParents: List<ReviewNote> = emptyList(),
) : DialogWrapper(project) {
    private val kindBox = ComboBox(ReviewKind.entries.toTypedArray())
    private val messageArea = JBTextArea(6, 52)
    private val tagsField = JBTextField()
    private val parentList = JBList(availableParents)

    val kind: ReviewKind
        get() = kindBox.item

    val message: String
        get() = messageArea.text.trim()

    val tags: List<String>
        get() = ReviewNoteWorkflow.parseTags(tagsField.text)

    val dependsOn: List<String>
        get() = parentList.selectedValuesList.map(ReviewNote::id)

    init {
        title = if (initialKind == null) "Review note for AI agent" else "Edit review note"
        kindBox.renderer = ReviewKindRenderer()
        if (initialKind != null) kindBox.selectedItem = initialKind
        messageArea.text = initialMessage
        messageArea.lineWrap = true
        messageArea.wrapStyleWord = true
        parentList.cellRenderer = ReviewNoteParentRenderer()
        parentList.visibleRowCount = 4
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(messageArea)
        scrollPane.preferredSize = Dimension(560, 150)
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Type:"), kindBox)
            .addLabeledComponent(JBLabel("Tags:"), tagsField)
            .addLabeledComponentFillVertically(
                "Depends on:",
                JBScrollPane(parentList).apply { preferredSize = Dimension(560, 80) },
            )
            .addLabeledComponentFillVertically("Note:", scrollPane)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = messageArea

    override fun doValidate(): ValidationInfo? {
        if (message.isBlank()) return ValidationInfo("Enter the review note text", messageArea)
        runCatching { tags }.exceptionOrNull()?.let { return ValidationInfo(it.message ?: "Invalid tags", tagsField) }
        if (dependsOn.size > ReviewNoteWorkflow.MAX_DEPENDENCIES) {
            return ValidationInfo(
                "Select no more than ${ReviewNoteWorkflow.MAX_DEPENDENCIES} dependencies",
                parentList,
            )
        }
        return null
    }

    private class ReviewKindRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (component is javax.swing.JLabel && value is ReviewKind) {
                component.text = value.title
                component.icon = ReviewNotePresentations.forKind(value).icon()
            }
            return component
        }
    }

    private class ReviewNoteParentRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (component is javax.swing.JLabel && value is ReviewNote) {
                component.text = "${value.kind} · ${value.location.workspacePath} — ${value.message.take(80)}"
            }
            return component
        }
    }
}
