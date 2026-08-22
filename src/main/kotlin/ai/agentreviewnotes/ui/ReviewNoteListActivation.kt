package ai.agentreviewnotes.ui

import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

internal object ReviewNoteListActivation {
    private const val DETAILS_ACTION = "agent-review-notes.details"
    private const val NAVIGATE_ACTION = "agent-review-notes.navigate"

    fun install(list: JList<*>, openDetails: () -> Unit, navigate: () -> Unit) {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.clickCount != 2) return
                val index = list.locationToIndex(event.point)
                if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return
                list.selectedIndex = index
                openDetails()
            }
        })
        list.bindSelectionAction("ENTER", DETAILS_ACTION, openDetails)
        list.bindSelectionAction("F4", NAVIGATE_ACTION, navigate)
    }

    private fun JList<*>.bindSelectionAction(keyStroke: String, actionKey: String, action: () -> Unit) {
        getInputMap(JList.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), actionKey)
        actionMap.put(actionKey, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                if (selectedIndex >= 0) action()
            }
        })
    }
}
