package ai.agentreviewnotes.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `skill documents authoritative repository symlink projection admission`() {
        val bundled = BundledReviewSkill.content()

        assertTrue(bundled.contains("lexical workspace path"))
        assertTrue(bundled.contains("canonical target"))
        assertTrue(bundled.contains("registered Git repository"))
        assertTrue(bundled.contains("arbitrary external symlink"))
        assertTrue(bundled.contains("nested symlink"))
        assertTrue(bundled.contains("standalone CLI has no IDE registry"))
        assertTrue(bundled.contains("verified Git top-level"))
        assertTrue(bundled.contains("valid unrelated Git repository"))
        assertTrue(bundled.contains("fake `.git`"))
        assertTrue(bundled.contains("inherited `GIT_*` repository-selection overrides"))
        assertFalse(bundled.contains("resolve through a symbolic link"))
        assertFalse(bundled.contains("inside the real project root without symlinks"))
    }
}
