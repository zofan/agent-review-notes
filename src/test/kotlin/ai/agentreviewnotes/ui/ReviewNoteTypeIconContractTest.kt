package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ReviewNoteTypeIconContractTest {
    @Test
    fun `note rows and kind filter use the shared kind icon`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNotesToolWindowFactory.kt"),
        )

        assertContains(source, "ReviewNotePresentations.forWireValue(value.kind).icon()")
        assertContains(source, "ReviewNotePresentations.forKind(kind).icon()")

        val dialogSource = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDialog.kt"),
        )
        assertContains(dialogSource, "ReviewNotePresentations.forKind(value).icon()")
    }
}
