package ai.agentreviewnotes.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ProjectSkillExampleTest {
    @Test
    fun `project ships an actionable agent review notes skill example`() {
        val skill = Path.of(System.getProperty("user.dir")).resolve("skills/agent-review-notes/SKILL.md")

        assertTrue(Files.isRegularFile(skill), "Missing $skill")
        val content = Files.readString(skill)
        assertTrue(content.startsWith("---\nname: agent-review-notes\n"))
        assertContains(content, "description: Use when processing Agent Review Notes")
        assertContains(content, ".idea/agent-review-notes/notes")
        assertContains(content, "agent.review.note.v1")
        assertContains(content, "agent.review.note.v2")
        assertContains(content, "`feature`")
        assertContains(content, "needs_reanchor")
        assertContains(content, "Do not edit source files")
        assertContains(content, "## Verification Checklist")
    }
}
