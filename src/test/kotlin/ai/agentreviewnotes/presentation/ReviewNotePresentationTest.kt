package ai.agentreviewnotes.presentation

import ai.agentreviewnotes.model.ReviewKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNotePresentationTest {
    @Test
    fun `every review kind has a distinct icon and editor palette`() {
        val presentations = ReviewKind.entries.map(ReviewNotePresentations::forKind)

        assertEquals(ReviewKind.entries.size, presentations.map { it.iconPath }.toSet().size)
        assertEquals(ReviewKind.entries.size, presentations.map { it.lightBackgroundRgb to it.lightBorderRgb }.toSet().size)
    }

    @Test
    fun `feature uses purple palette and ranks below bug`() {
        val feature = ReviewNotePresentations.forKind(ReviewKind.FEATURE)
        val bug = ReviewNotePresentations.forKind(ReviewKind.BUG)

        assertEquals("/icons/reviewNoteFeature.svg", feature.iconPath)
        assertEquals(0xF3E5F5, feature.lightBackgroundRgb)
        assertEquals(0x7B1FA2, feature.lightBorderRgb)
        assertEquals(0x45234F, feature.darkBackgroundRgb)
        assertEquals(0xCE93D8, feature.darkBorderRgb)
        assertEquals(true, feature.priority < bug.priority)
    }
}
