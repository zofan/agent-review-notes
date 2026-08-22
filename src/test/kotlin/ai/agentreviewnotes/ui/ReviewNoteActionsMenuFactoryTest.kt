package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewNoteActionsMenuFactoryTest {
    @Test
    fun `compact menu exposes all note mutation actions`() {
        val invoked = mutableListOf<String>()

        val menu = ReviewNoteActionsMenuFactory.create(
            onEdit = { invoked += "edit" },
            onDelete = { invoked += "delete" },
            onResolve = { invoked += "resolve" },
            onReopen = { invoked += "reopen" },
        )

        assertEquals("⋯", menu.button.text)
        assertEquals("More note actions", menu.button.toolTipText)
        assertEquals("More note actions", menu.button.accessibleContext.accessibleName)
        assertEquals(4, menu.popup.componentCount)

        menu.editItem.doClick()
        menu.deleteItem.doClick()
        menu.resolveItem.doClick()
        menu.reopenItem.doClick()

        assertEquals(listOf("edit", "delete", "resolve", "reopen"), invoked)
        assertTrue(menu.button.margin.left <= 6)
        assertTrue(menu.button.margin.right <= 6)
    }
}
