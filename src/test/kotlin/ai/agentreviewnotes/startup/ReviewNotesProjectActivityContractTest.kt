package ai.agentreviewnotes.startup

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNotesProjectActivityContractTest {
    @Test
    fun `store changes refresh project tree and editor daemon`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/startup/ReviewNotesProjectActivity.kt"),
        )

        assertContains(source, "store.addListener(project) { refreshPresentations(project) }")
        assertContains(source, "refreshProjectView(project)")
        assertContains(source, "restartDaemon(project, CACHE_RESTART_REASON)")
    }
}
