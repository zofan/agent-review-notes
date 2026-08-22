package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentSkillInstallButtonFactoryTest {
    @Test
    fun `button is labeled and invokes installation`() {
        var invoked = false
        val button = AgentSkillInstallButtonFactory.create { invoked = true }

        assertEquals("Install SKILL", button.text)
        assertEquals("Install the Agent Review Notes skill in this project", button.toolTipText)
        assertEquals("Install Agent Review Notes SKILL", button.accessibleContext.accessibleName)
        button.doClick()
        assertTrue(invoked)
    }
}
