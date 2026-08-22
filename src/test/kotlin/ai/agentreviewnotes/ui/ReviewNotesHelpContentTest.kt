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
        assertContains(help, "compact note text")
        assertContains(help, "highlighted in the editor")
        assertContains(help, "date range")
        assertContains(help, "Feature")
        assertContains(help, "Ctrl+Alt+Shift+R")
        assertContains(help, "above its anchor")
        assertContains(help, "compact note text")
        assertContains(help, "one Edit button")
        assertContains(help, "Type, Status, and Note together")
        assertContains(help, "regardless of its current status")
        assertContains(help, "Save or Cancel")
        assertContains(help, "Delete")
        assertContains(help, ".idea/agent-review-notes/notes")
        assertContains(help, "Settings | Keymap | Agent Review Notes")
    }
}
