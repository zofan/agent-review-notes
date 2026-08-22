package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNotesToolbarContractTest {
    @Test
    fun `панель не содержит кнопок деталей навигации и действий над заметкой`() {
        val source = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNotesToolWindowFactory.kt"),
        )

        assertFalse(source.contains("viewButton"))
        assertFalse(source.contains("navigateButton"))
        assertFalse(source.contains("actionsMenu.button"))
        assertTrue(source.contains("ReviewNoteContextMenu.install(notes"))
    }
}
