package ai.agentreviewnotes.model

const val REVIEW_NOTE_SCHEMA = "agent.review.note.v1"

enum class ReviewKind(val wireValue: String, val title: String) {
    BLOCKER("blocker", "Blocker"),
    BUG("bug", "Bug"),
    QUESTION("question", "Question"),
    SUGGESTION("suggestion", "Suggestion"),
}

enum class ReviewStatus(val wireValue: String) {
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    WONT_FIX("wont_fix"),
    NEEDS_REANCHOR("needs_reanchor"),
    STALE("stale"),
}

data class ReviewNote(
    val schema: String = REVIEW_NOTE_SCHEMA,
    val id: String,
    val status: String,
    val kind: String,
    val message: String,
    val location: NoteLocation,
    val anchor: NoteAnchor,
    val createdAt: String,
    val resolution: NoteResolution? = null,
)

data class NoteLocation(
    val workspacePath: String,
    val vcsRoot: String?,
    val vcsPath: String?,
    val head: String?,
    val fileSha256: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
    val branch: String? = null,
)

object ReviewNoteBranch {
    fun isVisible(noteBranch: String?, currentBranch: String?): Boolean =
        noteBranch == null || noteBranch == currentBranch

    fun isVisible(
        noteBranch: String?,
        noteVcsRoot: String?,
        currentBranch: String?,
        currentVcsRoot: String?,
    ): Boolean = noteBranch == null ||
        (noteVcsRoot != null && noteVcsRoot == currentVcsRoot && noteBranch == currentBranch)
}

data class NoteAnchor(
    val selection: String,
    val prefix: String,
    val suffix: String,
    val symbol: String?,
)

data class NoteResolution(
    val summary: String,
    val resolvedAt: String,
    val fileSha256: String?,
)
