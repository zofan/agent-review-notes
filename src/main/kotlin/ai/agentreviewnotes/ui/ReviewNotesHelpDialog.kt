package ai.agentreviewnotes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

internal class ReviewNotesHelpDialog(project: Project) : DialogWrapper(project) {
    init {
        title = "Agent Review Notes Help"
        init()
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(
        JBTextArea(ReviewNotesHelpContent.text, 16, 68).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            caretPosition = 0
            accessibleContext.accessibleName = "Agent Review Notes usage help"
        },
    ).apply {
        preferredSize = Dimension(720, 360)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
