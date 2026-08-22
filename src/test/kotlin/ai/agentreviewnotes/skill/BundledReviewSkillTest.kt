package ai.agentreviewnotes.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledReviewSkillTest {
    @Test
    fun `plugin resource contains the canonical project skill`() {
        val canonical = Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("skills/agent-review-notes/SKILL.md"),
        )

        val bundled = BundledReviewSkill.content()

        assertEquals(canonical, bundled)
        assertTrue(bundled.startsWith("---\nname: agent-review-notes\n"))
    }
}
