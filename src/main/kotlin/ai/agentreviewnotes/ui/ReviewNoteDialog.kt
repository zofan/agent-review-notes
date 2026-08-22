package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class ReviewNoteDialog(
    project: Project,
    initialKind: ReviewKind? = null,
    initialMessage: String = "",
) : DialogWrapper(project) {
    private val kindBox = ComboBox(ReviewKind.entries.toTypedArray())
    private val messageArea = JBTextArea(6, 52)

    val kind: ReviewKind
        get() = kindBox.item

    val message: String
        get() = messageArea.text.trim()

    init {
        title = if (initialKind == null) "Замечание для AI-агента" else "Изменить замечание"
        kindBox.renderer = ReviewKindRenderer()
        if (initialKind != null) kindBox.selectedItem = initialKind
        messageArea.text = initialMessage
        messageArea.lineWrap = true
        messageArea.wrapStyleWord = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(messageArea)
        scrollPane.preferredSize = Dimension(560, 150)
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Тип:"), kindBox)
            .addLabeledComponentFillVertically("Замечание:", scrollPane)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = messageArea

    override fun doValidate(): ValidationInfo? {
        if (message.isBlank()) return ValidationInfo("Напиши текст замечания", messageArea)
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
            if (component is javax.swing.JLabel && value is ReviewKind) component.text = value.title
            return component
        }
    }
}
