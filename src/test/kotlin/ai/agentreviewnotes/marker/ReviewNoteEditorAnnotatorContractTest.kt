package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNoteEditorAnnotatorContractTest {
    @Test
    fun `plugin registers annotator that highlights resolved note range`() {
        val descriptor = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorAnnotator.kt"),
        )

        assertContains(descriptor, "ai.agentreviewnotes.marker.ReviewNoteEditorAnnotator")
        assertContains(source, "ReviewNoteHighlightRange.resolve")
        assertContains(source, "newSilentAnnotation")
        assertContains(source, "enforcedTextAttributes")
    }
}
