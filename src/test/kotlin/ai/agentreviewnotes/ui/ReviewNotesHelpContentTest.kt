package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNotesHelpContentTest {
    @Test
    fun `help covers creation navigation filters storage and shortcut settings`() {
        val help = ReviewNotesHelpContent.text

        assertContains(help, "Ctrl+Alt+R")
        assertContains(help, "double-click")
        assertContains(help, "F4")
        assertContains(help, "right-click")
        assertContains(help, "Shift+F10")
        assertContains(help, "Menu key")
        assertContains(help, "gutter")
        assertContains(help, "highlighted in the editor")
        assertContains(help, "date range")
        assertContains(help, "Feature")
        assertContains(help, ".idea/agent-review-notes/notes")
        assertContains(help, "Settings | Keymap | Agent Review Notes")
    }
}
