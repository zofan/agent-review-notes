package ai.agentreviewnotes

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PluginDescriptionContractTest {
    @Test
    fun `description is concise English and covers the feature set`() {
        val descriptor = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))
        val description = descriptor.substringAfter("<description><![CDATA[").substringBefore("]]></description>")

        assertFalse(Regex("[А-Яа-яЁё]").containsMatchIn(description))
        assertContains(description, "code, files, and directories")
        assertContains(description, "Git-aware anchors")
        assertContains(description, "tool window")
        assertContains(description, "note types, statuses, tags, filters")
        assertContains(description, "dependency-aware execution plans")
        assertContains(description, "versioned JSON contract")
        assertContains(description, "bundled AI-agent skill")
        assertContains(description, "Install SKILL")
        assertContains(description, "Copyright © Andrey Leonov")
    }
}
