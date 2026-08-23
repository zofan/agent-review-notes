package ai.agentreviewnotes.action

import ai.agentreviewnotes.marker.ReviewNoteEditorLineTarget
import ai.agentreviewnotes.marker.ReviewNoteEditorMarkup
import ai.agentreviewnotes.marker.ReviewNoteEditorQuery
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.ui.ReviewNoteDetailsService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor

class OpenReviewNoteAtCaretAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible =
            event.project != null &&
                event.getData(CommonDataKeys.EDITOR) != null &&
                event.getData(CommonDataKeys.VIRTUAL_FILE)?.isInLocalFileSystem == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val target = targetRange(editor)
        val noteIds = ReviewNoteEditorMarkup.matchingNoteIds(
            editor,
            target.startOffset,
            target.endOffset,
            target.includeEndPoint,
        )
        if (noteIds.isEmpty()) return
        val byId = project.service<ReviewNoteStore>().cachedList().associateBy { it.id }
        val notes = noteIds.mapNotNull(byId::get)
        project.service<ReviewNoteDetailsService>().showCandidates(notes, editor)
    }

    private fun targetRange(editor: Editor): ReviewNoteEditorQuery {
        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            return ReviewNoteEditorQuery(selection.selectionStart, selection.selectionEnd)
        }
        val document = editor.document
        val offset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val line = document.getLineNumber(offset)
        return ReviewNoteEditorLineTarget.resolve(
            lineStart = document.getLineStartOffset(line),
            lineEnd = document.getLineEndOffset(line),
            textLength = document.textLength,
            caretOffset = offset,
        )
    }
}
