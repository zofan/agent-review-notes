package ai.agentreviewnotes.marker

import ai.agentreviewnotes.presentation.ReviewNotePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

internal data class ReviewNoteEditorDecoration(
    val range: ReviewNoteHighlightRange,
    val textAttributes: TextAttributes,
    val gutterIconRenderer: GutterIconRenderer? = null,
)

internal object ReviewNoteEditorMarkup {
    private val ownerKey = Key.create<Boolean>("agent.review.notes.range.highlighter")

    fun textAttributes(presentation: ReviewNotePresentation): TextAttributes = TextAttributes(
        null,
        JBColor(Color(presentation.lightBackgroundRgb), Color(presentation.darkBackgroundRgb)),
        JBColor(Color(presentation.lightBorderRgb), Color(presentation.darkBorderRgb)),
        EffectType.ROUNDED_BOX,
        Font.PLAIN,
    )

    fun replace(editor: Editor, decorations: List<ReviewNoteEditorDecoration>) {
        val markup = editor.markupModel
        markup.allHighlighters
            .filter { it.getUserData(ownerKey) == true }
            .forEach(markup::removeHighlighter)

        decorations.forEach { decoration ->
            val range = decoration.range
            markup.addRangeHighlighter(
                range.startOffset,
                range.endOffset,
                HighlighterLayer.SELECTION - 1,
                decoration.textAttributes,
                HighlighterTargetArea.EXACT_RANGE,
            ).also { highlighter ->
                highlighter.putUserData(ownerKey, true)
                highlighter.gutterIconRenderer = decoration.gutterIconRenderer
                highlighter.errorStripeTooltip = decoration.gutterIconRenderer?.tooltipText
            }
        }
    }
}
