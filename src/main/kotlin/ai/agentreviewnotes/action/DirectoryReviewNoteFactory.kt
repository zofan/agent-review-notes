package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import java.nio.file.Path

internal data class DirectoryGitLocation(
    val vcsRoot: String?,
    val vcsPath: String?,
    val head: String?,
    val branch: String?,
)

internal object DirectoryReviewNoteFactory {
    fun gitLocation(
        projectRoot: Path,
        directory: Path,
        repositoryRoot: Path?,
        head: String?,
        branch: String?,
    ): DirectoryGitLocation {
        if (repositoryRoot == null || !repositoryRoot.startsWith(projectRoot) || !directory.startsWith(repositoryRoot)) {
            return DirectoryGitLocation(null, null, null, null)
        }
        return DirectoryGitLocation(
            vcsRoot = relativePath(projectRoot, repositoryRoot),
            vcsPath = relativePath(repositoryRoot, directory),
            head = head,
            branch = branch,
        )
    }

    fun create(
        workspacePath: String,
        vcsRoot: String?,
        vcsPath: String?,
        head: String?,
        branch: String?,
        kind: ReviewKind,
        message: String,
        id: String,
        createdAt: String,
    ): ReviewNote = ReviewNote(
        id = id,
        status = ReviewStatus.OPEN.wireValue,
        kind = kind.wireValue,
        message = message,
        location = NoteLocation(
            workspacePath = workspacePath,
            vcsRoot = vcsRoot,
            vcsPath = vcsPath,
            head = head,
            fileSha256 = "",
            startOffset = 0,
            endOffset = 0,
            startLine = 0,
            endLine = 0,
            branch = branch,
            target = "directory",
        ),
        anchor = NoteAnchor("", "", "", null),
        createdAt = createdAt,
    )

    private fun relativePath(root: Path, child: Path): String =
        root.relativize(child).toString().replace(java.io.File.separatorChar, '/')
}
