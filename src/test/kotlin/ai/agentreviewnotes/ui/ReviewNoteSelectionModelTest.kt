package ai.agentreviewnotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteSelectionModelTest {
    @Test
    fun `запрос выбора доставляется текущим слушателям`() {
        val model = ReviewNoteSelectionModel()
        val selected = mutableListOf<String>()
        val subscription = model.subscribe(selected::add)

        model.request("note-1")
        subscription.close()
        model.request("note-2")

        assertEquals(listOf("note-1"), selected)
    }

    @Test
    fun `последний запрос повторяется позднему слушателю`() {
        val model = ReviewNoteSelectionModel()
        model.request("note-1")
        val selected = mutableListOf<String>()

        model.subscribe(selected::add)

        assertEquals(listOf("note-1"), selected)
    }
}
