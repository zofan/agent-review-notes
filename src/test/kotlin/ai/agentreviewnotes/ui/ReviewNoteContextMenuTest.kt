package ai.agentreviewnotes.ui

import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteContextMenuTest {
    @Test
    fun `правый клик выбирает заметку под указателем и открывает меню действий`() {
        val list = JList(arrayOf("first", "second"))
        list.setSize(200, 100)
        val shownAt = mutableListOf<Pair<Int, Int>>()
        ReviewNoteContextMenu.install(list) { x, y -> shownAt += x to y }

        val event = MouseEvent(
            list,
            MouseEvent.MOUSE_RELEASED,
            1L,
            0,
            10,
            30,
            1,
            true,
            MouseEvent.BUTTON3,
        )
        list.mouseListeners.forEach { it.mouseReleased(event) }

        assertEquals(1, list.selectedIndex)
        assertEquals(listOf(10 to 30), shownAt)
    }

    @Test
    fun `правый клик вне строк не открывает меню действий`() {
        val list = JList(arrayOf("first"))
        list.fixedCellHeight = 20
        list.setSize(200, 100)
        var shown = 0
        ReviewNoteContextMenu.install(list) { _, _ -> shown++ }

        val event = MouseEvent(
            list,
            MouseEvent.MOUSE_RELEASED,
            1L,
            0,
            10,
            80,
            1,
            true,
            MouseEvent.BUTTON3,
        )
        list.mouseListeners.forEach { it.mouseReleased(event) }

        assertEquals(0, shown)
    }

    @Test
    fun `Shift F10 открывает меню действий для выбранной заметки`() {
        val list = JList(arrayOf("first", "second"))
        list.fixedCellHeight = 20
        list.setSize(200, 100)
        list.selectedIndex = 1
        val shownAt = mutableListOf<Pair<Int, Int>>()
        ReviewNoteContextMenu.install(list) { x, y -> shownAt += x to y }

        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK)
        val actionKey = list.inputMap.get(keyStroke)
        list.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(listOf(0 to 40), shownAt)
    }

    @Test
    fun `клавиша контекстного меню открывает действия для выбранной заметки`() {
        val list = JList(arrayOf("first", "second"))
        list.fixedCellHeight = 20
        list.setSize(200, 100)
        list.selectedIndex = 1
        val shownAt = mutableListOf<Pair<Int, Int>>()
        ReviewNoteContextMenu.install(list) { x, y -> shownAt += x to y }

        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)
        val actionKey = list.inputMap.get(keyStroke)
        list.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(listOf(0 to 40), shownAt)
    }
}
