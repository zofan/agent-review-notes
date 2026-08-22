package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNoteGutterIconRendererContractTest {
    @Test
    fun `gutter icon is selected from note kind presentation`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteGutterIconRenderer.kt"),
        )

        assertContains(source, "ReviewNotePresentations.forWireValue(note.kind)")
        assertContains(source, "IconLoader.getIcon")
        assertFalse(source.contains("AllIcons.General.BalloonInformation"))
    }
}
