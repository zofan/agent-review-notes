package ai.agentreviewnotes.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteBranchTest {
    @Test
    fun `заметка без ветки видна везде`() {
        assertTrue(ReviewNoteBranch.isVisible(noteBranch = null, currentBranch = "main"))
    }

    @Test
    fun `заметка видна на сохраненной ветке`() {
        assertTrue(ReviewNoteBranch.isVisible(noteBranch = "feature/review", currentBranch = "feature/review"))
    }

    @Test
    fun `заметка скрыта на другой ветке и detached head`() {
        assertFalse(ReviewNoteBranch.isVisible(noteBranch = "feature/review", currentBranch = "main"))
        assertFalse(ReviewNoteBranch.isVisible(noteBranch = "feature/review", currentBranch = null))
    }

    @Test
    fun `заметка скрыта в другом Git root даже на одноименной ветке`() {
        assertFalse(
            ReviewNoteBranch.isVisible(
                noteBranch = "main",
                noteVcsRoot = "services/api",
                currentBranch = "main",
                currentVcsRoot = "services/web",
            ),
        )
    }
}
