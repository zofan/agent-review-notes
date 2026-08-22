package ai.agentreviewnotes.ui

import javax.swing.JMenuItem
import javax.swing.JPopupMenu

internal data class ReviewNoteActionsMenu(
    val popup: JPopupMenu,
    val editItem: JMenuItem,
    val deleteItem: JMenuItem,
    val resolveItem: JMenuItem,
    val reopenItem: JMenuItem,
)

internal object ReviewNoteActionsMenuFactory {
    fun create(
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onResolve: () -> Unit,
        onReopen: () -> Unit,
    ): ReviewNoteActionsMenu {
        val popup = JPopupMenu()
        val editItem = popup.actionItem("Edit note", onEdit)
        val deleteItem = popup.actionItem("Delete note", onDelete)
        val resolveItem = popup.actionItem("Resolve note", onResolve)
        val reopenItem = popup.actionItem("Reopen note", onReopen)
        return ReviewNoteActionsMenu(popup, editItem, deleteItem, resolveItem, reopenItem)
    }

    private fun JPopupMenu.actionItem(title: String, action: () -> Unit): JMenuItem =
        JMenuItem(title).also { item ->
            item.addActionListener { action() }
            add(item)
        }
}
