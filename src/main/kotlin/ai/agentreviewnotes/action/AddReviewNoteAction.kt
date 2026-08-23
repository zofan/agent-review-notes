package ai.agentreviewnotes.action

import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNoteTargetBoundary
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
        val basePath = project?.basePath
        event.presentation.isEnabledAndVisible =
            project != null &&
                editor != null &&
                file?.isInLocalFileSystem == true &&
                basePath != null &&
                ReviewNoteTargetBoundary.isWorkspacePath(Path.of(basePath), Path.of(file.path))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val projectRoot = Path.of(requireNotNull(project.basePath)).toAbsolutePath().normalize()
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile)
        val repositoryRoot = repository?.root?.path?.let(Path::of)
        val repositoryHead = repository?.currentRevision
        val repositoryBranch = repository?.currentBranchName
        val sourcePath = Path.of(virtualFile.path)
        val dialog = ReviewNoteDialog(project)
        if (!dialog.showAndGet()) return

        val document = editor.document
        val range = selectedOrCurrentLine(document, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
        val modificationStamp = document.modificationStamp
        val kind = dialog.kind
        val message = dialog.message
        val store = project.service<ReviewNoteStore>()
        CompletableFuture.supplyAsync(
            {
                val preparedTarget = prepareTarget(
                    projectRoot = projectRoot,
                    sourcePath = sourcePath,
                    repositoryRoot = repositoryRoot,
                    repositoryHead = repositoryHead,
                    repositoryBranch = repositoryBranch,
                )
                ApplicationManager.getApplication().runReadAction<ReviewNote> {
                    check(document.modificationStamp == modificationStamp) {
                        "The document changed while the note was being created; try again"
                    }
                    buildNote(project, document, preparedTarget, range, kind, message)
                }
            },
            AppExecutorUtil.getAppExecutorService(),
        ).thenCompose(store::createAsync).whenComplete { _, error ->
            if (project.isDisposed) return@whenComplete
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (error != null) {
                    val cause = (error as? CompletionException)?.cause ?: error
                    Messages.showErrorDialog(project, cause.message ?: "Failed to save the note", "Agent Review Notes")
                    return@invokeLater
                }
                PsiDocumentManager.getInstance(project).getPsiFile(document)?.let {
                    DaemonCodeAnalyzer.getInstance(project).restart(it, this)
                }
                ToolWindowManager.getInstance(project).getToolWindow("Agent Review")?.show()
            }
        }
    }

    private fun prepareTarget(
        projectRoot: Path,
        sourcePath: Path,
        repositoryRoot: Path?,
        repositoryHead: String?,
        repositoryBranch: String?,
    ): PreparedTarget {
        val git = ReviewNoteGitLocationResolver.resolve(
            projectRoot = projectRoot,
            target = sourcePath,
            repositoryRoot = repositoryRoot,
            head = repositoryHead,
            branch = repositoryBranch,
        )
        val mapping = ReviewNoteGitLocationResolver.repositoryMapping(projectRoot, sourcePath, repositoryRoot)
        val file = ReviewNoteTargetBoundary.resolve(projectRoot, sourcePath, listOfNotNull(mapping))
        return PreparedTarget(projectRoot, file, git)
    }

    private fun buildNote(
        project: Project,
        document: Document,
        preparedTarget: PreparedTarget,
        range: TextRange,
        kind: ReviewKind,
        message: String,
    ): ReviewNote {
        val text = document.text
        val projectRoot = preparedTarget.projectRoot
        val file = preparedTarget.file
        val git = preparedTarget.git
        val selection = text.substring(range.startOffset, range.endOffset)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val symbol = psiFile?.findElementAt(range.startOffset.coerceAtMost((text.length - 1).coerceAtLeast(0)))
            ?.parentsWithSelf()
            ?.filterIsInstance<PsiNamedElement>()
            ?.firstOrNull()
            ?.name

        val location = NoteLocation(
            workspacePath = relativePath(projectRoot, file),
            vcsRoot = git.vcsRoot,
            vcsPath = git.vcsPath,
            head = git.head,
            fileSha256 = ReviewNoteAnchor.sha256(text),
            startOffset = range.startOffset,
            endOffset = range.endOffset,
            startLine = document.getLineNumber(range.startOffset) + 1,
            endLine = document.getLineNumber(lastSelectedOffset(range, text.length)) + 1,
            branch = git.branch,
        )
        val anchor = NoteAnchor(
            selection = selection,
            prefix = text.substring((range.startOffset - ANCHOR_CONTEXT).coerceAtLeast(0), range.startOffset),
            suffix = text.substring(range.endOffset, (range.endOffset + ANCHOR_CONTEXT).coerceAtMost(text.length)),
            symbol = symbol,
        )
        return ReviewNote(
            schema = kind.schema,
            id = UUID.randomUUID().toString(),
            status = ReviewStatus.OPEN.wireValue,
            kind = kind.wireValue,
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

    private data class PreparedTarget(
        val projectRoot: Path,
        val file: Path,
        val git: ReviewNoteGitLocation,
    )

    private companion object {
        const val ANCHOR_CONTEXT = 240
    }
}
