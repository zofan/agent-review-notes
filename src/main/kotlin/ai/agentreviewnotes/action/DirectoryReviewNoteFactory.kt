package ai.agentreviewnotes.action

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA_V3

internal object DirectoryReviewNoteFactory {
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
        tags: List<String>,
        dependsOn: List<String>,
    ): ReviewNote = ReviewNote(
        schema = REVIEW_NOTE_SCHEMA_V3,
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
        tags = tags,
        dependsOn = dependsOn,
    )
}
