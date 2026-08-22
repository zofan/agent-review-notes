package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteActionsMenuFactoryTest {
    @Test
    fun `контекстное меню содержит все действия над заметкой`() {
        val invoked = mutableListOf<String>()

        val menu = ReviewNoteActionsMenuFactory.create(
            onEdit = { invoked += "edit" },
            onDelete = { invoked += "delete" },
            onResolve = { invoked += "resolve" },
            onReopen = { invoked += "reopen" },
        )

        assertEquals(4, menu.popup.componentCount)

        menu.editItem.doClick()
        menu.deleteItem.doClick()
        menu.resolveItem.doClick()
        menu.reopenItem.doClick()

        assertEquals(listOf("edit", "delete", "resolve", "reopen"), invoked)
    }
}
