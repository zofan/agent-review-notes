package ai.agentreviewnotes.action

import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.REVIEW_NOTE_SCHEMA_V3
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewStatus

internal object FileReviewNoteFactory {
    fun create(
        workspacePath: String,
        vcsRoot: String?,
        vcsPath: String?,
        head: String?,
        branch: String?,
        text: String,
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
            fileSha256 = ReviewNoteAnchor.sha256(text),
            startOffset = 0,
            endOffset = text.length,
            startLine = 1,
            endLine = text.count { it == '\n' } + 1,
            branch = branch,
        ),
        anchor = NoteAnchor(
            selection = text,
            prefix = "",
            suffix = "",
            symbol = null,
        ),
        createdAt = createdAt,
        tags = tags,
        dependsOn = dependsOn,
    )

}
