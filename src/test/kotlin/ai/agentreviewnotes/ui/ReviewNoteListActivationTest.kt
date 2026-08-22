package ai.agentreviewnotes.ui

import java.awt.event.MouseEvent
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteListActivationTest {
    @Test
    fun `двойной клик по строке открывает детали выбранной заметки`() {
        val list = JList(arrayOf("first", "second"))
        list.setSize(200, 100)
        var details = 0
        var navigations = 0
        ReviewNoteListActivation.install(
            list,
            openDetails = { details++ },
            navigate = { navigations++ },
        )

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

        assertEquals(1, list.selectedIndex)
        assertEquals(1, details)
        assertEquals(0, navigations)
    }

    @Test
    fun `enter открывает детали выбранной заметки`() {
        val list = JList(arrayOf("first"))
        list.selectedIndex = 0
        var details = 0
        ReviewNoteListActivation.install(list, openDetails = { details++ }, navigate = {})

        val actionKey = list.inputMap.get(javax.swing.KeyStroke.getKeyStroke("ENTER"))
        list.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(1, details)
    }

    @Test
    fun `F4 переходит к месту выбранной заметки`() {
        val list = JList(arrayOf("first"))
        list.selectedIndex = 0
        var navigations = 0
        ReviewNoteListActivation.install(list, openDetails = {}, navigate = { navigations++ })

        val actionKey = list.inputMap.get(javax.swing.KeyStroke.getKeyStroke("F4"))
        list.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(1, navigations)
    }
}
