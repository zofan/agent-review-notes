package ai.agentreviewnotes.action

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNoteShortcutMetadataTest {
    @Test
    fun `add note shortcut is discoverable in the IDE keymap settings`() {
        val descriptor = Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("src/main/resources/META-INF/plugin.xml"),
        )

        assertContains(descriptor, "first-keystroke=\"ctrl alt R\"")
        assertContains(descriptor, "group id=\"AgentReviewNotes.KeymapGroup\"")
        assertContains(descriptor, "text=\"Agent Review Notes\"")
        assertContains(descriptor, "reference ref=\"AgentReviewNotes.AddNote\"")
        assertContains(descriptor, "reference ref=\"AgentReviewNotes.AddDirectoryNote\"")
        assertContains(descriptor, "id=\"AgentReviewNotes.OpenNoteAtCaret\"")
        assertContains(descriptor, "first-keystroke=\"ctrl alt shift R\"")
        assertContains(descriptor, "reference ref=\"AgentReviewNotes.OpenNoteAtCaret\"")
    }
}
