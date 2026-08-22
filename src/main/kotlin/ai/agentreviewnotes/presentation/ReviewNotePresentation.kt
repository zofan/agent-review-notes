package ai.agentreviewnotes.presentation

import ai.agentreviewnotes.model.ReviewKind
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

internal data class ReviewNotePresentation(
    val iconPath: String,
    val lightBackgroundRgb: Int,
    val lightBorderRgb: Int,
    val darkBackgroundRgb: Int,
    val darkBorderRgb: Int,
    val priority: Int,
) {
    fun icon(): Icon = IconLoader.getIcon(iconPath, ReviewNotePresentations::class.java)
}

internal object ReviewNotePresentations {
    fun forKind(kind: ReviewKind): ReviewNotePresentation = when (kind) {
        ReviewKind.BLOCKER -> ReviewNotePresentation(
            "/icons/reviewNoteBlocker.svg", 0xFFE0E0, 0xD32F2F, 0x5A2528, 0xFF6B6B, 500,
        )
        ReviewKind.BUG -> ReviewNotePresentation(
            "/icons/reviewNoteBug.svg", 0xFFF0D6, 0xE07A00, 0x5C4218, 0xFFB74D, 400,
        )
        ReviewKind.FEATURE -> ReviewNotePresentation(
            "/icons/reviewNoteFeature.svg", 0xF3E5F5, 0x7B1FA2, 0x45234F, 0xCE93D8, 300,
        )
        ReviewKind.QUESTION -> ReviewNotePresentation(
            "/icons/reviewNoteQuestion.svg", 0xE3F2FD, 0x1976D2, 0x183F5C, 0x64B5F6, 200,
        )
        ReviewKind.SUGGESTION -> ReviewNotePresentation(
            "/icons/reviewNoteSuggestion.svg", 0xE8F5E9, 0x388E3C, 0x244C2B, 0x81C784, 100,
        )
    }

    fun forWireValue(value: String): ReviewNotePresentation =
        forKind(ReviewKind.entries.firstOrNull { it.wireValue == value } ?: ReviewKind.SUGGESTION)
}
