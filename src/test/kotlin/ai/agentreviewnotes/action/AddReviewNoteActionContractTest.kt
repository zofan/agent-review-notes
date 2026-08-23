package ai.agentreviewnotes.action

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddReviewNoteActionContractTest {
    @Test
    fun `file notes use schema selected by review kind`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/action/AddReviewNoteAction.kt"),
        )

        assertTrue(source.contains("schema = kind.schema"))
    }

    @Test
    fun `file and directory actions do not reject workspace symlink projections`() {
        val fileAction = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/action/AddReviewNoteAction.kt"),
        )
        val directoryAction = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/action/AddDirectoryReviewNoteAction.kt"),
        )

        assertFalse(fileAction.contains("VFileProperty.SYMLINK"))
        assertFalse(directoryAction.contains("VFileProperty.SYMLINK"))
        assertFalse(fileAction.contains("ProjectFileIndex"))
        assertFalse(directoryAction.contains("ProjectFileIndex"))
        assertTrue(fileAction.contains("ReviewNoteTargetBoundary.isWorkspacePath"))
        assertTrue(directoryAction.contains("ReviewNoteTargetBoundary.isWorkspacePath"))
        assertTrue(fileAction.contains("ReviewNoteGitLocationResolver.repositoryMapping"))
        assertTrue(directoryAction.contains("ReviewNoteGitLocationResolver.repositoryMapping"))
        assertTrue(fileAction.contains("listOfNotNull(mapping)"))
        assertTrue(directoryAction.contains("listOfNotNull(mapping)"))
        assertTrue(fileAction.indexOf("val repository =") < fileAction.indexOf("CompletableFuture.supplyAsync"))
        assertTrue(directoryAction.indexOf("val repository =") < directoryAction.indexOf("CompletableFuture.supplyAsync"))
        assertFalse(fileAction.substringAfter("private fun buildNote").contains("GitRepositoryManager"))
    }
}
