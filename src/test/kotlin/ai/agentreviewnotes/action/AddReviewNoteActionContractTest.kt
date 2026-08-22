package ai.agentreviewnotes.action

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class AddReviewNoteActionContractTest {
    @Test
    fun `file notes use schema selected by review kind`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/action/AddReviewNoteAction.kt"),
        )

        assertTrue(source.contains("schema = kind.schema"))
    }
}
