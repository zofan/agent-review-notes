package ai.agentreviewnotes.ui

import java.awt.event.MouseEvent
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteListActivationTest {
    @Test
    fun `двойной клик по строке открывает выбранную заметку`() {
        val list = JList(arrayOf("first", "second"))
        list.setSize(200, 100)
        list.selectedIndex = 1
        var activations = 0
        ReviewNoteListActivation.install(list) { activations++ }

        val event = MouseEvent(
            list,
            MouseEvent.MOUSE_CLICKED,
            1L,
            0,
            10,
            30,
            2,
            false,
            MouseEvent.BUTTON1,
        )
        list.mouseListeners.forEach { it.mouseClicked(event) }

        assertEquals(1, activations)
    }

    @Test
    fun `enter открывает выбранную заметку`() {
        val list = JList(arrayOf("first"))
        list.selectedIndex = 0
        var activations = 0
        ReviewNoteListActivation.install(list) { activations++ }

        val actionKey = list.inputMap.get(javax.swing.KeyStroke.getKeyStroke("ENTER"))
        list.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(1, activations)
    }
}
