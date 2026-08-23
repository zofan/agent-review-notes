package ai.agentreviewnotes.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewNoteToolbarWrapLayoutTest {
    @Test
    fun `narrow toolbar reports wrapped preferred height`() {
        val toolbar = toolbar(width = 210)
        val narrowHeight = toolbar.preferredSize.height

        toolbar.setSize(400, 100)
        val wideHeight = toolbar.preferredSize.height

        assertTrue(narrowHeight > wideHeight)
    }

    @Test
    fun `narrow toolbar lays overflowing filters onto the next row`() {
        val toolbar = toolbar(width = 210)

        toolbar.doLayout()

        assertEquals(toolbar.getComponent(0).y, toolbar.getComponent(1).y)
        assertTrue(toolbar.getComponent(2).y > toolbar.getComponent(0).y)
    }

    @Test
    fun `border layout allocates wrapped height in the first resize layout pass`() {
        val toolbar = toolbar(width = 0)
        val parent = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            setSize(400, 400)
        }
        parent.doLayout()
        toolbar.doLayout()
        assertEquals(toolbar.preferredSize.height, toolbar.height)
        assertEquals(toolbar.getComponent(0).y, toolbar.getComponent(3).y)

        parent.setSize(210, 400)
        parent.doLayout()
        toolbar.doLayout()

        assertEquals(toolbar.preferredSize.height, toolbar.height)
        assertTrue(toolbar.getComponent(2).y > toolbar.getComponent(0).y)
    }

    private fun toolbar(width: Int): JPanel = JPanel(
        ReviewNoteToolbarWrapLayout(FlowLayout.LEFT, 4, 3),
    ).apply {
        repeat(4) {
            add(JPanel().apply {
                preferredSize = Dimension(90, 24)
                minimumSize = preferredSize
            })
        }
        setSize(width, 100)
    }
}
