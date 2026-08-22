package ai.agentreviewnotes.action

import ai.agentreviewnotes.store.ReviewNotePathPolicy
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
        val vcsRoot = ReviewNotePathPolicy.relativeCanonical(projectRoot, repositoryRoot)
            ?: return ReviewNoteGitLocation(null, null, null, null)
        val vcsPath = ReviewNotePathPolicy.relativeCanonical(repositoryRoot, target)
            ?: return ReviewNoteGitLocation(null, null, null, null)
        return ReviewNoteGitLocation(
            vcsRoot = vcsRoot,
            vcsPath = vcsPath,
            head = head,
            branch = branch,
        )
    }
}
