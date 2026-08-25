package ai.agentreviewnotes.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        assertContains(content, "agent.review.note.v3")
        assertContains(content, "plan --tag")
        assertContains(content, "dependsOn")
        assertContains(content, "`feature`")
        assertContains(content, "needs_reanchor")
        assertContains(content, "Do not edit source files")
        assertContains(content, "## Verification Checklist")
    }

    @Test
    fun `skill stays synchronized with supported review note schemas`() {
        val project = Path.of(System.getProperty("user.dir"))
        val model = Files.readString(
            project.resolve("src/main/kotlin/ai/agentreviewnotes/model/ReviewNote.kt"),
        )
        val skill = Files.readString(project.resolve("skills/agent-review-notes/SKILL.md"))
        val script = Files.readString(project.resolve("skills/agent-review-notes/scripts/review_notes.py"))
        val schemaPattern = Regex("agent\\.review\\.note\\.v\\d+")
        val supportedSchemas = schemaPattern.findAll(model).map { it.value }.toSet()
        val skillSchemas = schemaPattern.findAll(skill).map { it.value }.toSet()
        val scriptSchemas = schemaPattern.findAll(script).map { it.value }.toSet()

        assertTrue(supportedSchemas.isNotEmpty(), "No review note schemas found in ReviewNote.kt")
        assertEquals(
            supportedSchemas,
            skillSchemas,
            "Update SKILL.md whenever the review note schema changes",
        )
        assertEquals(
            supportedSchemas,
            scriptSchemas,
            "Update the bundled review_notes.py whenever the review note schema changes",
        )
    }
}
