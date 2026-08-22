package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewNote
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

internal class ReviewNoteDetailsDialog(project: Project, note: ReviewNote) : DialogWrapper(project) {
    private val rows = ReviewNoteDetails.rows(note)

    init {
        title = "Подробности замечания"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        rows.forEach { row ->
            val component = if (row.label == "Сниппет" || row.label == "Замечание") {
                JBScrollPane(JBTextArea(row.value, 4, 56).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                }).apply { preferredSize = Dimension(620, 90) }
            } else {
                JBLabel(row.value)
            }
            builder.addLabeledComponent("${row.label}:", component)
        }
        return builder.panel
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
