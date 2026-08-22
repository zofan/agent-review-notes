package ai.agentreviewnotes.projectview

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNoteProjectViewDecoratorContractTest {
    @Test
    fun `decorator получает VirtualFile через API ProjectViewNode`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/projectview/ReviewNoteProjectViewDecorator.kt"),
        )

        assertContains(source, "node.virtualFile")
        assertFalse(source.contains("node.value as? VirtualFile"))
    }
}
