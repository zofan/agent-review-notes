package ai.agentreviewnotes.action

import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteRepositoryMapping
import java.nio.file.Path

internal data class ReviewNoteGitLocation(
    val vcsRoot: String?,
    val vcsPath: String?,
    val head: String?,
    val branch: String?,
)

internal object ReviewNoteGitLocationResolver {
    fun resolve(
        projectRoot: Path,
        target: Path,
        repositoryRoot: Path?,
        head: String?,
        branch: String?,
    ): ReviewNoteGitLocation {
        if (repositoryRoot == null) {
            return ReviewNoteGitLocation(null, null, null, null)
        }
        val workspaceRepositoryRoot = workspaceRepositoryRoot(projectRoot, target, repositoryRoot)
            ?: return ReviewNoteGitLocation(null, null, null, null)
        val vcsRoot = ReviewNotePathPolicy.relativeCanonical(projectRoot, workspaceRepositoryRoot)
            ?: return ReviewNoteGitLocation(null, null, null, null)
        val vcsPath = ReviewNotePathPolicy.relativeCanonical(workspaceRepositoryRoot, target)
            ?: return ReviewNoteGitLocation(null, null, null, null)
        return ReviewNoteGitLocation(
            vcsRoot = vcsRoot,
            vcsPath = vcsPath,
            head = head,
            branch = branch,
        )
    }

    fun workspaceVcsRoot(projectRoot: Path, target: Path, repositoryRoot: Path): String? {
        val workspaceRoot = workspaceRepositoryRoot(projectRoot, target, repositoryRoot) ?: return null
        return ReviewNotePathPolicy.relativeCanonical(projectRoot, workspaceRoot)
    }

    fun repositoryMapping(projectRoot: Path, target: Path, repositoryRoot: Path?): ReviewNoteRepositoryMapping? {
        if (repositoryRoot == null) return null
        val workspaceRoot = workspaceRepositoryRoot(projectRoot, target, repositoryRoot) ?: return null
        return ReviewNoteRepositoryMapping(workspaceRoot, repositoryRoot)
    }

    private fun workspaceRepositoryRoot(projectRoot: Path, target: Path, repositoryRoot: Path): Path? {
        val root = projectRoot.toAbsolutePath().normalize()
        val child = target.toAbsolutePath().normalize()
        val repository = repositoryRoot.toAbsolutePath().normalize()
        if (!child.startsWith(root)) return null
        if (repository.startsWith(root) && child.startsWith(repository)) return repository

        val realRepository = runCatching { repository.toRealPath() }.getOrNull() ?: return null
        var candidate: Path? = child
        while (candidate != null && candidate.startsWith(root)) {
            val realCandidate = runCatching { candidate.toRealPath() }.getOrNull()
            if (realCandidate == realRepository) return candidate
            if (candidate == root) break
            candidate = candidate.parent
        }
        return null
    }
}
