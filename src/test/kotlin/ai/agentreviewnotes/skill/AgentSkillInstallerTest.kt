package ai.agentreviewnotes.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSkillInstallerTest {
    @Test
    fun `existing agent skill directory is selected`() = withTempProject { project ->
        val existing = project.resolve(".claude/skills").createDirectories()

        assertEquals(existing, AgentSkillInstaller.resolveSkillRoot(project))
    }

    @Test
    fun `standard agents directory is used when project has no skill directory`() = withTempProject { project ->
        assertEquals(project.resolve(".agents/skills"), AgentSkillInstaller.resolveSkillRoot(project))
    }

    @Test
    fun `install creates skill once and never overwrites different content`() = withTempProject { project ->
        val first = AgentSkillInstaller.install(project, "bundled skill")
        val second = AgentSkillInstaller.install(project, "bundled skill")
        first.target.writeText("project customization")
        val conflict = AgentSkillInstaller.install(project, "bundled skill")

        assertEquals(AgentSkillInstallStatus.INSTALLED, first.status)
        assertEquals(AgentSkillInstallStatus.ALREADY_INSTALLED, second.status)
        assertEquals(AgentSkillInstallStatus.CONFLICT, conflict.status)
        assertEquals("project customization", Files.readString(first.target))
    }

    @Test
    fun `symlinked skill directory is not used`() = withTempProject { project ->
        val outside = createTempDirectory("agent-review-notes-outside")
        try {
            project.resolve(".claude").createDirectories()
            Files.createSymbolicLink(project.resolve(".claude/skills"), outside)

            val result = AgentSkillInstaller.install(project, "bundled skill")

            assertEquals(project.resolve(".agents/skills/agent-review-notes/SKILL.md"), result.target)
            assertFalse(Files.exists(outside.resolve("agent-review-notes/SKILL.md")))
        } finally {
            deleteRecursively(outside)
        }
    }

    private fun withTempProject(block: (Path) -> Unit) {
        val project = createTempDirectory("agent-review-notes-project")
        try {
            block(project)
        } finally {
            deleteRecursively(project)
        }
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
        assertTrue(Files.notExists(root))
    }
}
