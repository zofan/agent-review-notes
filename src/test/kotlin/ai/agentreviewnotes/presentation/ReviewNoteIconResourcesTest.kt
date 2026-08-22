package ai.agentreviewnotes.presentation

import ai.agentreviewnotes.model.ReviewKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteIconResourcesTest {
    @Test
    fun `every review kind icon is a safe packaged svg`() {
        ReviewKind.entries.forEach { kind ->
            val iconPath = ReviewNotePresentations.forKind(kind).iconPath.removePrefix("/")
            val path = Path.of("src/main/resources").resolve(iconPath)
            assertTrue(Files.isRegularFile(path), "Missing icon for ${kind.wireValue}: $path")
            val svg = Files.readString(path)
            assertContains(svg, "<svg")
            assertContains(svg, "viewBox=\"0 0 16 16\"")
            assertFalse(svg.contains("<script", ignoreCase = true))
            assertFalse(svg.contains("<image", ignoreCase = true))
            assertFalse(svg.contains("href=", ignoreCase = true))
        }
    }
}
