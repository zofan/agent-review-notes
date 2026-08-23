package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.ReviewNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteFacetFiltersTest {
    @Test
    fun `branch options contain all exact branches and unscoped notes`() {
        val options = ReviewNoteBranchFilter.options(
            listOf(
                note(branch = "main", vcsRoot = "golang/handler"),
                note(branch = "feature/z", vcsRoot = "golang/sage"),
                note(branch = "main", vcsRoot = "golang/trainer"),
                note(branch = null, vcsRoot = null),
            ),
        )

        assertEquals(
            listOf("All branches", "feature/z", "main", "No branch"),
            options.map { it.title },
        )
        assertTrue(ReviewNoteBranchFilter.isVisible("other", ReviewNoteBranchFilter.all))
        assertTrue(ReviewNoteBranchFilter.isVisible("feature/z", ReviewNoteBranchFilter.exact("feature/z")))
        assertFalse(ReviewNoteBranchFilter.isVisible("main", ReviewNoteBranchFilter.exact("feature/z")))
        assertTrue(ReviewNoteBranchFilter.isVisible(null, ReviewNoteBranchFilter.withoutBranch))
    }

    @Test
    fun `repository options are sorted exact and include project root and unscoped notes`() {
        val notes = listOf(
            note(branch = "main", vcsRoot = "golang/trainer"),
            note(branch = "main", vcsRoot = ""),
            note(branch = null, vcsRoot = null),
            note(branch = "main", vcsRoot = "golang/handler"),
            note(branch = "feature", vcsRoot = "golang/handler"),
        )

        val options = ReviewNoteRepositoryFilter.options(notes)

        assertEquals(
            listOf("All repositories", "Project root", "golang/handler", "golang/trainer", "No repository"),
            options.map { it.title },
        )
        assertTrue(ReviewNoteRepositoryFilter.isVisible("golang/handler", ReviewNoteRepositoryFilter.all))
        assertTrue(
            ReviewNoteRepositoryFilter.isVisible(
                "golang/handler",
                ReviewNoteRepositoryFilter.exact("golang/handler"),
            ),
        )
        assertFalse(ReviewNoteRepositoryFilter.isVisible("golang/trainer", ReviewNoteRepositoryFilter.exact("")))
        assertTrue(ReviewNoteRepositoryFilter.isVisible(null, ReviewNoteRepositoryFilter.withoutRepository))
    }

    private fun note(branch: String?, vcsRoot: String?) = ReviewNote(
        id = "note-${branch.orEmpty()}-${vcsRoot.orEmpty()}",
        status = "open",
        kind = "bug",
        message = "message",
        location = NoteLocation(
            workspacePath = "src/main.go",
            vcsRoot = vcsRoot,
            vcsPath = vcsRoot?.let { "src/main.go" },
            head = null,
            fileSha256 = "a".repeat(64),
            startOffset = 0,
            endOffset = 1,
            startLine = 1,
            endLine = 1,
            branch = branch,
        ),
        anchor = NoteAnchor("x", "", "", null),
        createdAt = "2026-08-23T00:00:00Z",
    )
}
