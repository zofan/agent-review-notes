package ai.agentreviewnotes.action

import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.ui.ReviewNoteDialog
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class AddReviewNoteAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible =
            project != null &&
                editor != null &&
                file?.isInLocalFileSystem == true &&
                ProjectFileIndex.getInstance(project).isInContent(file)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val dialog = ReviewNoteDialog(project)
        if (!dialog.showAndGet()) return

        val document = editor.document
        val range = selectedOrCurrentLine(document, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
        val modificationStamp = document.modificationStamp
        val kind = dialog.kind.wireValue
        val message = dialog.message
        val store = project.service<ReviewNoteStore>()
        CompletableFuture.supplyAsync(
            {
                ApplicationManager.getApplication().runReadAction<ReviewNote> {
                    check(document.modificationStamp == modificationStamp) {
                        "Документ изменился во время создания заметки; повторите действие"
                    }
                    buildNote(project, document, file.path, range, kind, message)
                }
            },
            AppExecutorUtil.getAppExecutorService(),
        ).thenCompose(store::createAsync).whenComplete { _, error ->
            if (project.isDisposed) return@whenComplete
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (error != null) {
                    val cause = (error as? CompletionException)?.cause ?: error
                    Messages.showErrorDialog(project, cause.message ?: "Не удалось сохранить заметку", "Agent Review Notes")
                    return@invokeLater
                }
                PsiDocumentManager.getInstance(project).getPsiFile(document)?.let {
                    DaemonCodeAnalyzer.getInstance(project).restart(it, this)
                }
                ToolWindowManager.getInstance(project).getToolWindow("Agent Review")?.show()
            }
        }
    }

    private fun buildNote(
        project: Project,
        document: Document,
        filePath: String,
        range: TextRange,
        kind: String,
        message: String,
    ): ReviewNote {
        val text = document.text
        val projectRoot = Path.of(requireNotNull(project.basePath)).normalize()
        val file = Path.of(filePath).normalize()
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
        val repository = virtualFile?.let { GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(it) }
        val vcsRootPath = repository?.root?.path?.let(Path::of)?.normalize()
        val selection = text.substring(range.startOffset, range.endOffset)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val symbol = psiFile?.findElementAt(range.startOffset.coerceAtMost((text.length - 1).coerceAtLeast(0)))
            ?.parentsWithSelf()
            ?.filterIsInstance<PsiNamedElement>()
            ?.firstOrNull()
            ?.name

        val location = NoteLocation(
            workspacePath = relativePath(projectRoot, file),
            vcsRoot = vcsRootPath?.let { relativePath(projectRoot, it) },
            vcsPath = vcsRootPath?.let { relativePath(it, file) },
            head = repository?.currentRevision,
            fileSha256 = ReviewNoteAnchor.sha256(text),
            startOffset = range.startOffset,
            endOffset = range.endOffset,
            startLine = document.getLineNumber(range.startOffset) + 1,
            endLine = document.getLineNumber(lastSelectedOffset(range, text.length)) + 1,
        )
        val anchor = NoteAnchor(
            selection = selection,
            prefix = text.substring((range.startOffset - ANCHOR_CONTEXT).coerceAtLeast(0), range.startOffset),
            suffix = text.substring(range.endOffset, (range.endOffset + ANCHOR_CONTEXT).coerceAtMost(text.length)),
            symbol = symbol,
        )
        return ReviewNote(
            id = UUID.randomUUID().toString(),
            status = ReviewStatus.OPEN.wireValue,
            kind = kind,
            message = message,
            location = location,
            anchor = anchor,
            createdAt = Instant.now().toString(),
        )
    }

    private fun selectedOrCurrentLine(document: Document, start: Int, end: Int): TextRange {
        if (start != end) return TextRange(start, end)
        val line = document.getLineNumber(start)
        return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
    }

    private fun lastSelectedOffset(range: TextRange, textLength: Int): Int {
        if (textLength == 0) return 0
        return (range.endOffset - 1).coerceIn(0, textLength - 1)
    }

    private fun relativePath(root: Path, child: Path): String =
        runCatching { root.relativize(child).toString() }.getOrElse { child.toString() }
            .replace(java.io.File.separatorChar, '/')

    private fun com.intellij.psi.PsiElement.parentsWithSelf(): Sequence<com.intellij.psi.PsiElement> =
        generateSequence(this) { it.parent }

    private companion object {
        const val ANCHOR_CONTEXT = 240
    }
}
