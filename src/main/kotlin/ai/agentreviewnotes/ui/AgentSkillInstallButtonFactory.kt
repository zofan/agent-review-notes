package ai.agentreviewnotes.ui

import javax.swing.JButton

internal object AgentSkillInstallButtonFactory {
    fun create(action: () -> Unit): JButton = JButton("Install SKILL").apply {
        toolTipText = "Install the Agent Review Notes skill in this project"
        accessibleContext.accessibleName = "Install Agent Review Notes SKILL"
        addActionListener { action() }
    }
}
