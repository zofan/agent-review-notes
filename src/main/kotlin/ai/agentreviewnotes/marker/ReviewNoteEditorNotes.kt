package ai.agentreviewnotes.marker

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

internal object ReviewNoteEditorNotes {
    fun forFile(project: Project, virtualFile: VirtualFile): List<ReviewNote> {
        val filePath = virtualFile.canonicalPath ?: return emptyList()
        val basePath = project.basePath ?: return emptyList()
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath)?.canonicalPath
            ?.let(Path::of) ?: return emptyList()
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile)
        val currentBranch = repository?.currentBranchName
        val currentVcsRoot = repository?.root?.canonicalPath?.let { rootPath ->
            ReviewNotePathPolicy.relativeCanonical(projectRoot, Path.of(rootPath))
        }
        val workspacePath = ReviewNotePathPolicy.relativeCanonical(projectRoot, Path.of(filePath)) ?: return emptyList()

        return project.service<ReviewNoteStore>().cachedList().filter { note ->
            note.status == ReviewStatus.OPEN.wireValue &&
                note.location.target != "directory" &&
                note.location.workspacePath == workspacePath &&
                ReviewNoteBranch.isVisible(
                    noteBranch = note.location.branch,
                    noteVcsRoot = note.location.vcsRoot,
                    currentBranch = currentBranch,
                    currentVcsRoot = currentVcsRoot,
                )
        }
    }
}
