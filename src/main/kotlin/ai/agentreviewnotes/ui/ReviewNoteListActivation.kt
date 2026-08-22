package ai.agentreviewnotes.ui

import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

internal object ReviewNoteListActivation {
    private const val ACTIVATE_ACTION = "agent-review-notes.activate"

    fun install(list: JList<*>, activate: () -> Unit) {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.clickCount != 2) return
                val index = list.locationToIndex(event.point)
                if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return
                list.selectedIndex = index
                activate()
            }
        })
        list.getInputMap(JList.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), ACTIVATE_ACTION)
        list.actionMap.put(ACTIVATE_ACTION, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                if (list.selectedIndex >= 0) activate()
            }
        })
    }
}
