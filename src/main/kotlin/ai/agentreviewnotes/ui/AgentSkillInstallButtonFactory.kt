package ai.agentreviewnotes.ui

import javax.swing.JButton

internal object AgentSkillInstallButtonFactory {
    fun create(action: () -> Unit): JButton = JButton("Add to project").apply {
        toolTipText = "Install the Agent Review Notes skill in this project"
        accessibleContext.accessibleName = "Add Agent Review Notes skill to project"
        addActionListener { action() }
    }
}
