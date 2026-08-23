package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewNote

internal enum class ReviewNoteBranchFilterMode {
    ALL,
    EXACT,
    WITHOUT_BRANCH,
}

internal data class ReviewNoteBranchFilterOption(
    val title: String,
    val mode: ReviewNoteBranchFilterMode,
    val branch: String? = null,
) {
    override fun toString(): String = title
}

internal object ReviewNoteBranchFilter {
    val all = ReviewNoteBranchFilterOption("All branches", ReviewNoteBranchFilterMode.ALL)
    val withoutBranch = ReviewNoteBranchFilterOption("No branch", ReviewNoteBranchFilterMode.WITHOUT_BRANCH)

    fun exact(branch: String) = ReviewNoteBranchFilterOption(branch, ReviewNoteBranchFilterMode.EXACT, branch)

    fun options(notes: List<ReviewNote>): List<ReviewNoteBranchFilterOption> = buildList {
        add(all)
        notes.mapNotNull { it.location.branch }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .mapTo(this, ::exact)
        if (notes.any { it.location.branch == null }) add(withoutBranch)
    }

    fun isVisible(noteBranch: String?, selected: ReviewNoteBranchFilterOption): Boolean = when (selected.mode) {
        ReviewNoteBranchFilterMode.ALL -> true
        ReviewNoteBranchFilterMode.EXACT -> noteBranch == selected.branch
        ReviewNoteBranchFilterMode.WITHOUT_BRANCH -> noteBranch == null
    }
}

internal enum class ReviewNoteRepositoryFilterMode {
    ALL,
    EXACT,
    WITHOUT_REPOSITORY,
}

internal data class ReviewNoteRepositoryFilterOption(
    val title: String,
    val mode: ReviewNoteRepositoryFilterMode,
    val vcsRoot: String? = null,
) {
    override fun toString(): String = title
}

internal object ReviewNoteRepositoryFilter {
    val all = ReviewNoteRepositoryFilterOption("All repositories", ReviewNoteRepositoryFilterMode.ALL)
    val withoutRepository = ReviewNoteRepositoryFilterOption(
        "No repository",
        ReviewNoteRepositoryFilterMode.WITHOUT_REPOSITORY,
    )

    fun exact(vcsRoot: String) = ReviewNoteRepositoryFilterOption(
        title = vcsRoot.ifEmpty { "Project root" },
        mode = ReviewNoteRepositoryFilterMode.EXACT,
        vcsRoot = vcsRoot,
    )

    fun options(notes: List<ReviewNote>): List<ReviewNoteRepositoryFilterOption> = buildList {
        add(all)
        notes.mapNotNull { it.location.vcsRoot }
            .distinct()
            .sortedWith(compareBy<String>({ it.isNotEmpty() }, { it.lowercase() }, { it }))
            .mapTo(this, ::exact)
        if (notes.any { it.location.vcsRoot == null }) add(withoutRepository)
    }

    fun isVisible(noteVcsRoot: String?, selected: ReviewNoteRepositoryFilterOption): Boolean = when (selected.mode) {
        ReviewNoteRepositoryFilterMode.ALL -> true
        ReviewNoteRepositoryFilterMode.EXACT -> noteVcsRoot == selected.vcsRoot
        ReviewNoteRepositoryFilterMode.WITHOUT_REPOSITORY -> noteVcsRoot == null
    }
}
