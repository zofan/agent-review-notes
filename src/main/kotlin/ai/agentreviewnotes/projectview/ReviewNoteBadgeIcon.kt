package ai.agentreviewnotes.projectview

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

internal class ReviewNoteBadgeIcon(private val delegate: Icon) : Icon {
    override fun getIconWidth(): Int = delegate.iconWidth

    override fun getIconHeight(): Int = delegate.iconHeight

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        delegate.paintIcon(component, graphics, x, y)
        val diameter = 6
        val badgeX = x + iconWidth - diameter
        val badgeY = y + iconHeight - diameter
        graphics.color = Color(0xD9, 0x73, 0x0D)
        graphics.fillOval(badgeX, badgeY, diameter, diameter)
        graphics.color = Color.WHITE
        graphics.drawOval(badgeX, badgeY, diameter - 1, diameter - 1)
    }
}
