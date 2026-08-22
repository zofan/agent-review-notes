package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNoteEditorMarkupTest {
    @Test
    fun `editor markup owns visible exact ranges and stale-highlighter removal`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorMarkup.kt"),
        )

        assertContains(source, "markup.allHighlighters")
        assertContains(source, "removeHighlighter")
        assertContains(source, "addRangeHighlighter")
        assertContains(source, "HighlighterLayer.SELECTION - 1")
        assertContains(source, "HighlighterTargetArea.EXACT_RANGE")
        assertContains(source, "EffectType.ROUNDED_BOX")
        assertContains(source, "gutterIconRenderer")
    }
}
