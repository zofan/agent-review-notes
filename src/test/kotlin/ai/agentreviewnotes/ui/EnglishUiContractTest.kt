package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnglishUiContractTest {
    @Test
    fun `user interface sources contain no Cyrillic text`() {
        val root = Path.of(System.getProperty("user.dir"))
        val uiRoots = listOf("ui", "action", "anchor", "marker")
            .map { root.resolve("src/main/kotlin/ai/agentreviewnotes/$it") }
        val offenders = uiRoots.flatMap { directory ->
            Files.walk(directory).use { paths ->
                paths.filter { it.extension == "kt" }
                    .filter { CYRILLIC.containsMatchIn(Files.readString(it)) }
                    .map(root::relativize)
                    .toList()
            }
        }

        assertTrue(offenders.isEmpty(), "Cyrillic UI text remains in: $offenders")
    }

    @Test
    fun `action metadata is English while plugin description remains Russian`() {
        val root = Path.of(System.getProperty("user.dir"))
        val descriptor = Files.readString(root.resolve("src/main/resources/META-INF/plugin.xml"))
        val description = DESCRIPTION.find(descriptor)?.value.orEmpty()
        val withoutDescription = descriptor.replace(DESCRIPTION, "<description/>")

        assertTrue(CYRILLIC.containsMatchIn(description), "Russian plugin description must be preserved")
        assertFalse(CYRILLIC.containsMatchIn(withoutDescription), "Action metadata must be English")
    }

    private companion object {
        val CYRILLIC = Regex("[А-Яа-яЁё]")
        val DESCRIPTION = Regex("<description>.*?</description>", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
