package ai.agentreviewnotes.ui

import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReviewNoteActionButtonFactoryTest {
    @Test
    fun `иконка действия сохраняет tooltip и доступное имя без текста`() {
        val icon = ImageIcon(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB))
        var clicked = false

        val button = ReviewNoteActionButtonFactory.create(icon, "Просмотреть") { clicked = true }
        button.doClick()

        assertSame(icon, button.icon)
        assertEquals("", button.text)
        assertEquals("Просмотреть", button.toolTipText)
        assertEquals("Просмотреть", button.accessibleContext.accessibleName)
        assertTrue(clicked)
    }

    @Test
    fun `compact кнопка вдвое уже обычной при той же высоте`() {
        val icon = ImageIcon(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB))
        val regular = ReviewNoteActionButtonFactory.create(icon, "Refresh") {}
        val compact = ReviewNoteActionButtonFactory.createCompact(icon, "Refresh") {}

        assertEquals(regular.preferredSize.width / 2, compact.preferredSize.width)
        assertEquals(regular.preferredSize.height, compact.preferredSize.height)
    }
}
