package ai.agentreviewnotes.ui

import java.awt.Dimension
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JButton

internal object ReviewNoteActionButtonFactory {
    fun create(icon: Icon, description: String, action: () -> Unit): JButton = JButton(icon).apply {
        text = ""
        toolTipText = description
        accessibleContext.accessibleName = description
        addActionListener { action() }
    }

    fun createCompact(icon: Icon, description: String, action: () -> Unit): JButton =
        create(icon, description, action).apply {
            val regularSize = preferredSize
            val compactSize = Dimension(regularSize.width / 2, regularSize.height)
            margin = Insets(0, 0, 0, 0)
            preferredSize = compactSize
            minimumSize = compactSize
        }
}
