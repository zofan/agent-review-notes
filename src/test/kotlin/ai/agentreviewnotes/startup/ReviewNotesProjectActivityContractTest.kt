package ai.agentreviewnotes.startup

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNotesProjectActivityContractTest {
    @Test
    fun `store changes refresh project tree and editor daemon`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/startup/ReviewNotesProjectActivity.kt"),
        )

        assertContains(source, "store.addListener(project) { refreshPresentations(project, highlighter) }")
        assertContains(source, "refreshProjectView(project)")
        assertContains(source, "highlighter.refreshAll()")
        assertContains(source, "restartDaemon(project, CACHE_RESTART_REASON)")
    }

    @Test
    fun `source file rename and move refresh open editor decorations`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/startup/ReviewNotesProjectActivity.kt"),
        )

        assertContains(source, "VFileMoveEvent")
        assertContains(source, "VFilePropertyChangeEvent")
        assertContains(source, "VirtualFile.PROP_NAME")
        assertContains(source, "isSourcePathChange")
        assertContains(source, "highlighter.refreshAll()")
        assertFalse(source.contains("else if (events.any(::isSourcePathChange))"))
    }
}
