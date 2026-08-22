package ai.agentreviewnotes.marker

import ai.agentreviewnotes.presentation.ReviewNotePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.event.MouseEvent

internal data class ReviewNoteEditorDecoration(
    val range: ReviewNoteHighlightRange,
    val textAttributes: TextAttributes,
    val targetPointAtEnd: Boolean? = null,
    val noteId: String? = null,
    val priority: Int = 0,
    val blockInlayRenderer: ReviewNoteBlockInlayRenderer? = null,
)

internal object ReviewNoteEditorMarkup {
    private val ownerKey = Key.create<Boolean>("agent.review.notes.range.highlighter")
    private val noteIdKey = Key.create<String>("agent.review.notes.note.id")
    private val priorityKey = Key.create<Int>("agent.review.notes.note.priority")
    private val targetPointAtEndKey = Key.create<Boolean>("agent.review.notes.target.point.at.end")
    private val inlayOwnerKey = Key.create<Boolean>("agent.review.notes.block.inlay")
    private val inlayClickInstalledKey = Key.create<Boolean>("agent.review.notes.inlay.click.installed")

    fun textAttributes(presentation: ReviewNotePresentation): TextAttributes = TextAttributes(
        null,
        JBColor(Color(presentation.lightBackgroundRgb), Color(presentation.darkBackgroundRgb)),
        JBColor(Color(presentation.lightBorderRgb), Color(presentation.darkBorderRgb)),
        EffectType.ROUNDED_BOX,
        Font.PLAIN,
    )

    fun matchingNoteIds(
        editor: Editor,
        queryStart: Int,
        queryEnd: Int,
        includeEndPoint: Boolean = false,
    ): List<String> {
        val spans = editor.markupModel.allHighlighters.mapNotNull { highlighter ->
            if (highlighter.getUserData(ownerKey) != true) return@mapNotNull null
            val noteId = highlighter.getUserData(noteIdKey) ?: return@mapNotNull null
            val pointAtEnd = highlighter.getUserData(targetPointAtEndKey)
            val startOffset = when (pointAtEnd) {
                true -> highlighter.endOffset
                false -> highlighter.startOffset
                null -> highlighter.startOffset
            }
            val endOffset = if (pointAtEnd == null) highlighter.endOffset else startOffset
            ReviewNoteEditorSpan(
                noteId = noteId,
                startOffset = startOffset,
                endOffset = endOffset,
                priority = highlighter.getUserData(priorityKey) ?: 0,
            )
        }
        return ReviewNoteEditorTarget.matchingIds(
            spans,
            queryStart,
            queryEnd,
            includeEndPoint,
        )
    }

    fun replace(editor: Editor, decorations: List<ReviewNoteEditorDecoration>): Int {
        installInlayClickHandler(editor)
        val markup = editor.markupModel
        markup.allHighlighters
            .filter { it.getUserData(ownerKey) == true }
            .forEach(markup::removeHighlighter)

        val inlayModel = editor.inlayModel
        var inlayCount = 0
        inlayModel.execute(true) {
            inlayModel.getBlockElementsInRange(0, editor.document.textLength)
                .filter { it.getUserData(inlayOwnerKey) == true }
                .forEach { it.dispose() }

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
                    decoration.noteId?.let { highlighter.putUserData(noteIdKey, it) }
                    decoration.targetPointAtEnd?.let { highlighter.putUserData(targetPointAtEndKey, it) }
                    highlighter.putUserData(priorityKey, decoration.priority)
                }
                decoration.blockInlayRenderer?.let { renderer ->
                    val inlay = inlayModel.addBlockElement(
                        range.startOffset,
                        false,
                        true,
                        decoration.priority,
                        renderer,
                    )
                    if (inlay != null) {
                        inlay.putUserData(inlayOwnerKey, true)
                        inlayCount += 1
                    }
                }
            }
        }
        return inlayCount
    }

    private fun installInlayClickHandler(editor: Editor) {
        if (editor.getUserData(inlayClickInstalledKey) == true) return
        editor.putUserData(inlayClickInstalledKey, true)
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseClicked(event: EditorMouseEvent) {
                if (event.mouseEvent.button != MouseEvent.BUTTON1 || event.mouseEvent.isConsumed) return
                val inlay = event.editor.inlayModel.getElementAt(event.mouseEvent.point) ?: return
                val renderer = inlay.renderer as? ReviewNoteBlockInlayRenderer ?: return
                event.mouseEvent.consume()
                renderer.openDetails()
            }
        })
    }
}
