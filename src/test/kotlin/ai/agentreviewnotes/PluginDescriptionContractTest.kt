package ai.agentreviewnotes

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class PluginDescriptionContractTest {
    @Test
    fun `description explains bundled agent skill`() {
        val descriptor = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))

        assertContains(descriptor, "bundled AI-agent skill")
        assertContains(descriptor, "поставляется со встроенным skill")
        assertContains(descriptor, "Install SKILL")
        assertContains(descriptor, "Copyright © Andrey Loenov")
    }
}
