package ai.agentreviewnotes.marker

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.presentation.ReviewNotePresentation
import ai.agentreviewnotes.ui.ReviewNoteDetailsService
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import kotlin.math.max
import kotlin.math.min

internal class ReviewNoteBlockInlayRenderer(
    private val project: Project,
    private val note: ReviewNote,
    private val presentation: ReviewNotePresentation,
) : EditorCustomElementRenderer {
    private val displayText = ReviewNoteInlayText.display(
        kindTitle = note.kind.replaceFirstChar { it.uppercase() },
        message = note.message,
    )

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val metrics = editor.contentComponent.getFontMetrics(font)
        val icon = presentation.icon()
        val naturalWidth = HORIZONTAL_PADDING * 2 + icon.iconWidth + ICON_GAP + metrics.stringWidth(displayText)
        val availableWidth = max(MIN_WIDTH, editor.contentComponent.width - JBUI.scale(32))
        return min(naturalWidth, availableWidth)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int =
        max(inlay.editor.lineHeight + JBUI.scale(4), JBUI.scale(24))

    override fun paint(
        inlay: Inlay<*>,
        graphics: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes,
    ) {
        val editor = inlay.editor
        val g = graphics.create() as Graphics2D
        try {
            g.clip(targetRegion)
            val x = targetRegion.x.toInt()
            val y = targetRegion.y.toInt() + JBUI.scale(1)
            val width = targetRegion.width.toInt().coerceAtLeast(1)
            val height = (targetRegion.height.toInt() - JBUI.scale(2)).coerceAtLeast(1)
            val arc = JBUI.scale(8)
            g.color = JBColor(
                Color(presentation.lightBackgroundRgb),
                Color(presentation.darkBackgroundRgb),
            )
            g.fillRoundRect(x, y, width - 1, height - 1, arc, arc)
            g.color = JBColor(
                Color(presentation.lightBorderRgb),
                Color(presentation.darkBorderRgb),
            )
            g.drawRoundRect(x, y, width - 1, height - 1, arc, arc)

            val icon = presentation.icon()
            val iconX = x + HORIZONTAL_PADDING
            val iconY = y + (height - icon.iconHeight) / 2
            icon.paintIcon(editor.contentComponent, g, iconX, iconY)

            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            g.font = font
            g.color = editor.colorsScheme.defaultForeground
            val metrics = g.fontMetrics
            val textX = iconX + icon.iconWidth + ICON_GAP
            val baseline = y + (height - metrics.height) / 2 + metrics.ascent
            val availableTextWidth = (x + width - HORIZONTAL_PADDING - textX).coerceAtLeast(0)
            val fittedText = ReviewNoteInlayText.fit(displayText, availableTextWidth, metrics::stringWidth)
            g.drawString(fittedText, textX, baseline)
        } finally {
            g.dispose()
        }
    }

    fun openDetails() {
        if (!project.isDisposed) project.service<ReviewNoteDetailsService>().show(note)
    }

    private companion object {
        val HORIZONTAL_PADDING = JBUI.scale(8)
        val ICON_GAP = JBUI.scale(6)
        val MIN_WIDTH = JBUI.scale(240)
    }
}
