package ai.agentreviewnotes.ui

import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.KeyStroke

internal object ReviewNoteContextMenu {
    private const val SHOW_ACTION = "agent-review-notes.show-actions"

    fun install(list: JList<*>, showMenu: (x: Int, y: Int) -> Unit) {
        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                showIfRequested(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                showIfRequested(event)
            }

            private fun showIfRequested(event: MouseEvent) {
                if (!event.isPopupTrigger) return
                val index = list.locationToIndex(event.point)
                if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return
                list.selectedIndex = index
                showMenu(event.x, event.y)
            }
        })
        val action = object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                val index = list.selectedIndex
                if (index < 0) return
                val bounds = list.getCellBounds(index, index) ?: return
                showMenu(bounds.x, bounds.y + bounds.height)
            }
        }
        list.getInputMap(JList.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK),
            SHOW_ACTION,
        )
        list.getInputMap(JList.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), SHOW_ACTION)
        list.actionMap.put(SHOW_ACTION, action)
    }
}
