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
    fun `Codex skill directory is selected`() = withTempProject { project ->
        val existing = project.resolve(".codex/skills").createDirectories()

        assertEquals(existing, AgentSkillInstaller.resolveSkillRoot(project))
    }

    @Test
    fun `OpenCode skill directory is selected`() = withTempProject { project ->
        val existing = project.resolve(".opencode/skills").createDirectories()

        assertEquals(existing, AgentSkillInstaller.resolveSkillRoot(project))
    }

    @Test
    fun `Hermes skill directory is selected`() = withTempProject { project ->
        val existing = project.resolve(".hermes/skills").createDirectories()

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
    fun `install writes packaged scripts and preserves conflicting script`() = withTempProject { project ->
        val files = mapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )

        val first = AgentSkillInstaller.install(project, files)
        val script = first.target.parent.resolve("scripts/review_notes.py")
        assertEquals("bundled script", Files.readString(script))
        script.writeText("project customization")
        val conflict = AgentSkillInstaller.install(project, files)

        assertEquals(AgentSkillInstallStatus.INSTALLED, first.status)
        assertEquals(AgentSkillInstallStatus.CONFLICT, conflict.status)
        assertEquals("project customization", Files.readString(script))
    }

    @Test
    fun `package conflict does not create missing package directories`() = withTempProject { project ->
        val skill = project.resolve(".agents/skills/agent-review-notes").createDirectories()
        skill.resolve("SKILL.md").writeText("project customization")

        val result = AgentSkillInstaller.install(
            project,
            mapOf("SKILL.md" to "bundled skill", "scripts/review_notes.py" to "bundled script"),
        )

        assertEquals(AgentSkillInstallStatus.CONFLICT, result.status)
        assertFalse(Files.exists(skill.resolve("scripts")))
    }

    @Test
    fun `install rejects an intermediate symlink even when external content matches`() = withTempProject { project ->
        val files = mapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )
        val skill = project.resolve(".agents/skills/agent-review-notes").createDirectories()
        skill.resolve("SKILL.md").writeText("bundled skill")
        val outside = createTempDirectory("agent-review-notes-external-scripts")
        try {
            outside.resolve("review_notes.py").writeText("bundled script")
            Files.createSymbolicLink(skill.resolve("scripts"), outside)

            val result = AgentSkillInstaller.install(project, files)

            assertEquals(AgentSkillInstallStatus.CONFLICT, result.status)
            assertEquals("bundled script", Files.readString(outside.resolve("review_notes.py")))
        } finally {
            deleteRecursively(outside)
        }
    }

    @Test
    fun `throwable publication failure still cleans staging`() = withTempProject { project ->
        val failure = kotlin.runCatching {
            AgentSkillInstaller.install(
                project,
                mapOf("SKILL.md" to "bundled skill", "scripts/review_notes.py" to "bundled script"),
            ) { throw AssertionError("injected fatal failure") }
        }.exceptionOrNull()

        assertTrue(failure is AssertionError)
        assertFalse(Files.exists(project.resolve(".agents/skills/agent-review-notes")))
        Files.list(project.resolve(".agents/skills")).use { entries ->
            assertFalse(entries.anyMatch { it.fileName.toString().startsWith(".agent-review-notes-") })
        }
    }

    @Test
    fun `concurrent staging payload is rejected before publication`() = withTempProject { project ->
        val files = linkedMapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )

        val failure = kotlin.runCatching {
            AgentSkillInstaller.install(project, files) { target ->
                val skillRoot = target.parent.parent
                val staging = Files.list(skillRoot).use { entries ->
                    entries.filter { it.fileName.toString().startsWith(".agent-review-notes-") }
                        .findFirst()
                        .orElseThrow()
                }
                staging.resolve("payload.txt").writeText("injected")
            }
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertFalse(Files.exists(project.resolve(".agents/skills/agent-review-notes")))
        Files.list(project.resolve(".agents/skills")).use { entries ->
            assertFalse(entries.anyMatch { it.fileName.toString().startsWith(".agent-review-notes-") })
        }
    }

    @Test
    fun `extra existing package entry is a conflict`() = withTempProject { project ->
        val files = linkedMapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )
        val skill = project.resolve(".agents/skills/agent-review-notes").createDirectories()
        skill.resolve("scripts").createDirectories().resolve("review_notes.py").writeText("bundled script")
        skill.resolve("SKILL.md").writeText("bundled skill")
        skill.resolve("custom.txt").writeText("custom")

        val result = AgentSkillInstaller.install(project, files)

        assertEquals(AgentSkillInstallStatus.CONFLICT, result.status)
        assertEquals("custom", Files.readString(skill.resolve("custom.txt")))
    }

    @Test
    fun `noncanonical package key is rejected`() = withTempProject { project ->
        val failure = kotlin.runCatching {
            AgentSkillInstaller.install(
                project,
                mapOf("SKILL.md" to "bundled skill", "scripts//review_notes.py" to "script"),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(Files.exists(project.resolve(".agents/skills/agent-review-notes")))
    }

    @Test
    fun `concurrent package creation is never overwritten`() = withTempProject { project ->
        val files = linkedMapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )
        lateinit var concurrentSkill: Path

        val failure = kotlin.runCatching {
            AgentSkillInstaller.install(project, files) { target ->
                concurrentSkill = target.createDirectories()
                concurrentSkill.resolve("SKILL.md").writeText("concurrent customization")
            }
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals("concurrent customization", Files.readString(concurrentSkill.resolve("SKILL.md")))
        assertFalse(Files.exists(concurrentSkill.resolve("scripts")))
        Files.list(project.resolve(".agents/skills")).use { entries ->
            assertFalse(entries.anyMatch { it.fileName.toString().startsWith(".agent-review-notes-") })
        }
    }

    @Test
    fun `concurrent empty package creation is never replaced`() = withTempProject { project ->
        val files = linkedMapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )
        lateinit var concurrentSkill: Path

        val failure = kotlin.runCatching {
            AgentSkillInstaller.install(project, files) { target ->
                concurrentSkill = target.createDirectories()
            }
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(Files.isDirectory(concurrentSkill))
        assertFalse(Files.exists(concurrentSkill.resolve("SKILL.md")))
        Files.list(project.resolve(".agents/skills")).use { entries ->
            assertFalse(entries.anyMatch { it.fileName.toString().startsWith(".agent-review-notes-") })
        }
    }

    @Test
    fun `partial matching package is a conflict and is not upgraded in place`() = withTempProject { project ->
        val files = linkedMapOf(
            "SKILL.md" to "bundled skill",
            "scripts/review_notes.py" to "bundled script",
        )
        val skill = project.resolve(".agents/skills/agent-review-notes").createDirectories()
        skill.resolve("SKILL.md").writeText("bundled skill")

        val result = AgentSkillInstaller.install(project, files)

        assertEquals(AgentSkillInstallStatus.CONFLICT, result.status)
        assertEquals("bundled skill", Files.readString(skill.resolve("SKILL.md")))
        assertFalse(Files.exists(skill.resolve("scripts")))
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
