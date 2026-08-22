package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNoteEditorHighlighterContractTest {
    @Test
    fun `plugin uses editor markup lifecycle instead of a language-specific ANY annotator`() {
        val descriptor = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))
        val markup = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorMarkup.kt"),
        )
        val lifecycle = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/startup/ReviewNotesProjectActivity.kt"),
        )
        val highlighter = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorHighlighter.kt"),
        )

        assertFalse(descriptor.contains("<annotator language=\"ANY\""))
        assertContains(markup, "addRangeHighlighter")
        assertContains(markup, "HighlighterTargetArea.EXACT_RANGE")
        assertFalse(markup.contains("gutterIconRenderer"))
        assertContains(lifecycle, "FileEditorManagerListener.FILE_EDITOR_MANAGER")
        assertContains(lifecycle, "addDocumentListener")
        assertContains(lifecycle, "highlighter.refreshAll()")
        assertContains(highlighter, "getAppScheduledExecutorService")
        assertContains(highlighter, "runReadAction")
        assertContains(highlighter, "modificationStamp")
        assertContains(highlighter, "ReviewNoteRefreshGeneration")
        assertContains(highlighter, "if (project.isDisposed)")
        assertFalse(highlighter.contains("catch (error: Throwable)"))
    }
}
