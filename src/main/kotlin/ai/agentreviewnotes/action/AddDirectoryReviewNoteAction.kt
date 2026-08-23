package ai.agentreviewnotes.action

import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNoteTargetBoundary
import ai.agentreviewnotes.ui.ReviewNoteDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
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
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val basePath = project?.basePath
        event.presentation.isEnabledAndVisible =
            project != null &&
                directory?.isDirectory == true &&
                directory.isInLocalFileSystem &&
                basePath != null &&
                ReviewNoteTargetBoundary.isWorkspacePath(Path.of(basePath), Path.of(directory.path))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val projectRoot = Path.of(requireNotNull(project.basePath)).toAbsolutePath().normalize()
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(directory)
        val repositoryRoot = repository?.root?.path?.let(Path::of)
        val repositoryHead = repository?.currentRevision
        val repositoryBranch = repository?.currentBranchName
        val requestedPath = Path.of(directory.path)
        val store = project.service<ReviewNoteStore>()
        val dialog = ReviewNoteDialog(project, availableParents = store.cachedList())
        if (!dialog.showAndGet()) return

        val kind = dialog.kind
        val message = dialog.message
        val tags = dialog.tags
        val dependsOn = dialog.dependsOn
        CompletableFuture.supplyAsync(
            {
                val git = ReviewNoteGitLocationResolver.resolve(
                    projectRoot = projectRoot,
                    target = requestedPath,
                    repositoryRoot = repositoryRoot,
                    head = repositoryHead,
                    branch = repositoryBranch,
                )
                val mapping = ReviewNoteGitLocationResolver.repositoryMapping(projectRoot, requestedPath, repositoryRoot)
                val directoryPath = ReviewNoteTargetBoundary.resolve(projectRoot, requestedPath, listOfNotNull(mapping))
                require(directoryPath != projectRoot) {
                    "The directory is outside the project or is the project root"
                }
                DirectoryReviewNoteFactory.create(
                    workspacePath = relativePath(projectRoot, directoryPath),
                    vcsRoot = git.vcsRoot,
                    vcsPath = git.vcsPath,
                    head = git.head,
                    branch = git.branch,
                    kind = kind,
                    message = message,
                    id = UUID.randomUUID().toString(),
                    createdAt = Instant.now().toString(),
                    tags = tags,
                    dependsOn = dependsOn,
                )
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

    private fun relativePath(root: Path, child: Path): String =
        root.relativize(child).toString().replace(java.io.File.separatorChar, '/')
}
