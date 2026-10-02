package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNoteTargetBoundary
import ai.agentreviewnotes.ui.ReviewNoteDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class AddDirectoryReviewNoteAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val target = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val basePath = project?.basePath
        val supportedType = target?.isDirectory == true || target?.isDirectory == false
        val isProjectRoot = basePath != null && target != null && Path.of(target.path).normalize() == Path.of(basePath).normalize()
        event.presentation.isEnabledAndVisible =
            project != null &&
                supportedType &&
                target.isInLocalFileSystem &&
                basePath != null &&
                !isProjectRoot &&
                ReviewNoteTargetBoundary.isWorkspacePath(Path.of(basePath), Path.of(requireNotNull(target).path))
        event.presentation.text = if (target?.isDirectory == false) {
            "Add Review Note to File"
        } else {
            "Add Review Note to Directory"
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val target = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val projectRoot = Path.of(requireNotNull(project.basePath)).toAbsolutePath().normalize()
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(target)
        CompletableFuture.supplyAsync(
            { prepareTarget(projectRoot, target, repository) },
            AppExecutorUtil.getAppExecutorService(),
        ).whenComplete { preparedTarget, error ->
            if (project.isDisposed) return@whenComplete
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (error != null) {
                    val cause = (error as? CompletionException)?.cause ?: error
                    Messages.showErrorDialog(
                        project,
                        cause.message ?: "This target cannot have a review note",
                        "Agent Review Notes",
                    )
                    return@invokeLater
                }
                showDialogAndCreate(project, projectRoot, preparedTarget)
            }
        }
    }

    private fun showDialogAndCreate(project: com.intellij.openapi.project.Project, projectRoot: Path, preparedTarget: PreparedTarget) {
        val store = project.service<ReviewNoteStore>()
        val dialog = ReviewNoteDialog(project, availableParents = store.cachedList())
        if (!dialog.showAndGet()) return

        val kind = dialog.kind
        val message = dialog.message
        val tags = dialog.tags
        val dependsOn = dialog.dependsOn
        CompletableFuture.supplyAsync(
            {
                if (preparedTarget.virtualFile.isDirectory) {
                    DirectoryReviewNoteFactory.create(
                        workspacePath = relativePath(projectRoot, preparedTarget.path),
                        vcsRoot = preparedTarget.git.vcsRoot,
                        vcsPath = preparedTarget.git.vcsPath,
                        head = preparedTarget.git.head,
                        branch = preparedTarget.git.branch,
                        kind = kind,
                        message = message,
                        id = UUID.randomUUID().toString(),
                        createdAt = Instant.now().toString(),
                        tags = tags,
                        dependsOn = dependsOn,
                    )
                } else {
                    ApplicationManager.getApplication().runReadAction<ReviewNote> {
                        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(preparedTarget.virtualFile)) {
                            "The selected file cannot be read as text"
                        }
                        FileReviewNoteFactory.create(
                            workspacePath = relativePath(projectRoot, preparedTarget.path),
                            vcsRoot = preparedTarget.git.vcsRoot,
                            vcsPath = preparedTarget.git.vcsPath,
                            head = preparedTarget.git.head,
                            branch = preparedTarget.git.branch,
                            text = document.immutableCharSequence.toString(),
                            kind = kind,
                            message = message,
                            id = UUID.randomUUID().toString(),
                            createdAt = Instant.now().toString(),
                            tags = tags,
                            dependsOn = dependsOn,
                        )
                    }
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
                ToolWindowManager.getInstance(project).getToolWindow("Agent Review")?.show()
            }
        }
    }

    private fun prepareTarget(
        projectRoot: Path,
        target: VirtualFile,
        repository: git4idea.repo.GitRepository?,
    ): PreparedTarget {
        require(target.isInLocalFileSystem) { "Review notes can only be attached to local files and directories" }
        val requestedPath = Path.of(target.path)
        val repositoryRoot = repository?.root?.path?.let(Path::of)
        val mapping = ReviewNoteGitLocationResolver.repositoryMapping(projectRoot, requestedPath, repositoryRoot)
        val admittedPath = ReviewNoteTargetBoundary.resolve(projectRoot, requestedPath, listOfNotNull(mapping))
        require(admittedPath != projectRoot) { "The project root cannot have a review note" }
        require(target.isDirectory || !target.fileType.isBinary) { "Review notes can only be attached to directories or text files" }
        if (!target.isDirectory) {
            ApplicationManager.getApplication().runReadAction {
                val document = requireNotNull(FileDocumentManager.getInstance().getDocument(target)) {
                    "The selected file cannot be read as text"
                }
                require(document.immutableCharSequence.toString().toByteArray(Charsets.UTF_8).size <= MAX_FILE_NOTE_CONTENT_BYTES) {
                    "The selected file is too large for a whole-file review note"
                }
            }
        }
        val git = ReviewNoteGitLocationResolver.resolve(
            projectRoot = projectRoot,
            target = requestedPath,
            repositoryRoot = repositoryRoot,
            head = repository?.currentRevision,
            branch = repository?.currentBranchName,
        )
        return PreparedTarget(admittedPath, target, git)
    }

    private fun relativePath(root: Path, child: Path): String =
        root.relativize(child).toString().replace(java.io.File.separatorChar, '/')

    private data class PreparedTarget(
        val path: Path,
        val virtualFile: VirtualFile,
        val git: ReviewNoteGitLocation,
    )

    private companion object {
        const val MAX_FILE_NOTE_CONTENT_BYTES = 512 * 1024
    }
}
