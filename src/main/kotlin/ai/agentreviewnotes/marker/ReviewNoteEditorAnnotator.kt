package ai.agentreviewnotes.marker

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.Color
import java.awt.Font

class ReviewNoteEditorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val psiFile = element as? PsiFile ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val notes = ReviewNoteEditorNotes.forFile(psiFile.project, virtualFile)
        if (notes.isEmpty()) return

        val currentText = psiFile.text
        val currentSha256 = ReviewNoteAnchor.sha256(currentText)
        notes.forEach { note ->
            val anchor = ReviewNoteAnchor.resolve(note, currentText, currentSha256) as? AnchorResult.Resolved
                ?: return@forEach
            val range = ReviewNoteHighlightRange.resolve(
                offset = anchor.offset,
                selectionLength = note.anchor.selection.length,
                textLength = currentText.length,
            ) ?: return@forEach

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(range.startOffset, range.endOffset))
                .enforcedTextAttributes(TEXT_ATTRIBUTES)
                .create()
        }
    }

    private companion object {
        val TEXT_ATTRIBUTES = TextAttributes(
            null,
            JBColor(Color(255, 237, 145), Color(92, 72, 24)),
            JBColor(Color(215, 142, 0), Color(230, 170, 45)),
            EffectType.ROUNDED_BOX,
            Font.PLAIN,
        )
    }
}
