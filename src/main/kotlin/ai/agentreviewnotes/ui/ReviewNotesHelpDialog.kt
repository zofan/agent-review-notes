package ai.agentreviewnotes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent

internal class ReviewNotesHelpDialog(project: Project) : DialogWrapper(project) {
    private val language = ComboBox(ReviewNotesHelpLanguage.entries.toTypedArray())
    private val help = JBTextArea(16, 68).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        accessibleContext.accessibleName = "Agent Review Notes usage help"
    }

    init {
        title = "Agent Review Notes Help"
        language.accessibleContext.accessibleName = "Help language"
        language.addActionListener { render() }
        render()
        init()
    }

    override fun createCenterPanel(): JComponent = javax.swing.JPanel(BorderLayout(0, 8)).apply {
        add(language, BorderLayout.NORTH)
        add(JBScrollPane(help).apply { preferredSize = Dimension(720, 360) }, BorderLayout.CENTER)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    private fun render() {
        help.text = ReviewNotesHelpContent.text(language.item)
        help.caretPosition = 0
    }
}
