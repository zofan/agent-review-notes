package ai.agentreviewnotes.marker

import ai.agentreviewnotes.action.ReviewNoteGitLocationResolver
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

internal data class ReviewNoteEditorFileSnapshot(
    val projectRoot: Path,
    val workspaceTarget: Path,
    val repositoryRoot: Path?,
    val currentBranch: String?,
)

internal object ReviewNoteEditorNotes {
    fun capture(project: Project, virtualFile: VirtualFile): ReviewNoteEditorFileSnapshot? {
        val basePath = project.basePath ?: return null
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile)
        return ReviewNoteEditorFileSnapshot(
            projectRoot = Path.of(basePath).toAbsolutePath().normalize(),
            workspaceTarget = Path.of(virtualFile.path).toAbsolutePath().normalize(),
            repositoryRoot = repository?.root?.path?.let(Path::of),
            currentBranch = repository?.currentBranchName,
        )
    }

    fun forSnapshot(project: Project, snapshot: ReviewNoteEditorFileSnapshot): List<ReviewNote> {
        val currentVcsRoot = snapshot.repositoryRoot?.let { repositoryRoot ->
            ReviewNoteGitLocationResolver.workspaceVcsRoot(
                snapshot.projectRoot,
                snapshot.workspaceTarget,
                repositoryRoot,
            )
        }
        val workspacePath = ReviewNotePathPolicy.relativeCanonical(
            snapshot.projectRoot,
            snapshot.workspaceTarget,
        ) ?: return emptyList()

        return project.service<ReviewNoteStore>().cachedList().filter { note ->
            note.status == ReviewStatus.OPEN.wireValue &&
                note.location.target != "directory" &&
                note.location.workspacePath == workspacePath &&
                ReviewNoteBranch.isVisible(
                    noteBranch = note.location.branch,
                    noteVcsRoot = note.location.vcsRoot,
                    currentBranch = snapshot.currentBranch,
                    currentVcsRoot = currentVcsRoot,
                )
        }
    }
}
