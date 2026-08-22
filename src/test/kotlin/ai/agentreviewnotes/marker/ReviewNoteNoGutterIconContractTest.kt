package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class ReviewNoteNoGutterIconContractTest {
    @Test
    fun `editor decorations rely on block inlays without gutter icons`() {
        val highlighter = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorHighlighter.kt"),
        )
        val markup = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorMarkup.kt"),
        )

        assertFalse(highlighter.contains("ReviewNoteGutterIconRenderer"))
        assertFalse(markup.contains("gutterIconRenderer"))
        assertFalse(
            Files.exists(Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteGutterIconRenderer.kt")),
        )
    }
}
