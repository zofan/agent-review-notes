package ai.agentreviewnotes.ui

import javax.swing.Icon
import javax.swing.JButton

internal object ReviewNoteActionButtonFactory {
    fun create(icon: Icon, description: String, action: () -> Unit): JButton = JButton(icon).apply {
        text = ""
        toolTipText = description
        accessibleContext.accessibleName = description
        addActionListener { action() }
    }
}
