package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNoteBlockInlayContractTest {
    @Test
    fun `editor markup replaces owned block inlays and installs click handling`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorMarkup.kt"),
        )

        assertContains(source, "getBlockElementsInRange")
        assertContains(source, "addBlockElement")
        assertContains(source, "inlayOwnerKey")
        assertContains(source, "ReviewNoteBlockInlayRenderer")
        assertContains(source, "addEditorMouseListener")
        assertContains(source, "openDetails()")
    }

    @Test
    fun `block renderer uses shared presentation and opens the details dialog`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteBlockInlayRenderer.kt"),
        )

        assertContains(source, "EditorCustomElementRenderer")
        assertContains(source, "ReviewNoteInlayText.display")
        assertContains(source, "presentation.icon()")
        assertContains(source, "ReviewNoteDetailsService")
        assertContains(source, "fillRoundRect")
        assertContains(source, "drawRoundRect")
    }

    @Test
    fun `background preparation carries an inlay renderer into the EDT apply step`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorHighlighter.kt"),
        )

        assertContains(source, "blockInlayRenderer = ReviewNoteBlockInlayRenderer(project, note, presentation)")
    }
}
