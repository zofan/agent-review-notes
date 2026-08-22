package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNotesHelpContentTest {
    @Test
    fun `help covers creation navigation filters storage and shortcut settings`() {
        val help = ReviewNotesHelpContent.text

        assertContains(help, "Ctrl+Alt+R")
        assertContains(help, "double-click")
        assertContains(help, "gutter")
        assertContains(help, "date range")
        assertContains(help, ".idea/agent-review-notes/notes")
        assertContains(help, "Settings | Keymap | Agent Review Notes")
    }
}
