package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteRefreshGenerationTest {
    @Test
    fun newerRequestInvalidatesOlderWorkForSameFile() {
        val generations = ReviewNoteRefreshGeneration()

        val first = generations.next("file.go")
        val second = generations.next("file.go")

        assertFalse(generations.isCurrent("file.go", first))
        assertTrue(generations.isCurrent("file.go", second))
    }

    @Test
    fun globalInvalidationRejectsWorkFromFormerFilePath() {
        val generations = ReviewNoteRefreshGeneration()
        val epoch = generations.currentEpoch()
        val generation = generations.next("old/path.go")

        generations.invalidateAll()

        assertFalse(generations.isCurrent("old/path.go", generation, epoch))
    }

    @Test
    fun staleEdtRequestIsRejectedBeforePerPathStateMutation() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteEditorHighlighter.kt"),
        )

        val guard = source.indexOf("if (!generations.isEpochCurrent(capturedEpoch)) return")
        val editorLookup = source.indexOf("FileEditorManager.getInstance(project).getAllEditors(file)")
        val generationAdvance = source.indexOf("generations.next(key)")

        assertTrue(guard >= 0)
        assertTrue(guard < editorLookup)
        assertTrue(guard < generationAdvance)
    }

    @Test
    fun requestsForDifferentFilesDoNotInvalidateEachOther() {
        val generations = ReviewNoteRefreshGeneration()

        val first = generations.next("first.go")
        generations.next("second.go")

        assertTrue(generations.isCurrent("first.go", first))
    }
}
