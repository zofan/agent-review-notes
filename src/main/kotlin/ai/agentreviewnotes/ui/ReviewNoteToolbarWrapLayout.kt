package ai.agentreviewnotes.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout

internal class ReviewNoteToolbarWrapLayout(
    align: Int,
    hgap: Int,
    vgap: Int,
) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, preferred = false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension = synchronized(target.treeLock) {
        val insets = target.insets
        val horizontalInsetsAndGap = insets.left + insets.right + hgap * 2
        val availableWidth = if (target.width > 0) target.width else Int.MAX_VALUE
        val rowWidthLimit = (availableWidth - horizontalInsetsAndGap).coerceAtLeast(1)
        val result = Dimension()
        var rowWidth = 0
        var rowHeight = 0

        target.components.asSequence()
            .filter { component -> component.isVisible }
            .map { component -> if (preferred) component.preferredSize else component.minimumSize }
            .forEach { size ->
                val nextWidth = if (rowWidth == 0) size.width else rowWidth + hgap + size.width
                if (rowWidth > 0 && nextWidth > rowWidthLimit) {
                    addRow(result, rowWidth, rowHeight)
                    rowWidth = size.width
                    rowHeight = size.height
                } else {
                    rowWidth = nextWidth
                    rowHeight = maxOf(rowHeight, size.height)
                }
            }
        addRow(result, rowWidth, rowHeight)
        result.width += horizontalInsetsAndGap
        result.height += insets.top + insets.bottom + vgap * 2
        result
    }

    private fun addRow(result: Dimension, rowWidth: Int, rowHeight: Int) {
        if (rowWidth == 0) return
        result.width = maxOf(result.width, rowWidth)
        if (result.height > 0) result.height += vgap
        result.height += rowHeight
    }
}
