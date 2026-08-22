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

    @Test
    fun `plugin resource contains the canonical query script`() {
        val canonical = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("skills/agent-review-notes/scripts/review_notes.py"),
        )

        val bundled = BundledReviewSkill.files()

        assertEquals(canonical, bundled.getValue("scripts/review_notes.py"))
        assertEquals(BundledReviewSkill.content(), bundled.getValue("SKILL.md"))
    }

    @Test
    fun `skill requires bounded metadata first note selection`() {
        val bundled = BundledReviewSkill.content()

        assertTrue(bundled.contains("scripts/review_notes.py"))
        assertTrue(bundled.contains("list --status open,in_progress --limit 20"))
        assertTrue(bundled.contains("Do not read every complete note JSON"))
    }
}
