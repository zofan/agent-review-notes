package ai.agentreviewnotes.skill

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

internal enum class AgentSkillInstallStatus {
    INSTALLED,
    ALREADY_INSTALLED,
    CONFLICT,
}

internal data class AgentSkillInstallResult(
    val target: Path,
    val status: AgentSkillInstallStatus,
)

internal object AgentSkillInstaller {
    private val candidateRoots = listOf(
        ".agents/skills",
        ".claude/skills",
        ".cursor/skills",
        ".github/skills",
        ".gemini/skills",
        ".hermes/skills",
        "skills",
    )

    fun resolveSkillRoot(projectRoot: Path): Path {
        val root = projectRoot.toRealPath()
        return candidateRoots.asSequence()
            .map(root::resolve)
            .firstOrNull(::isSafeDirectory)
            ?: root.resolve(candidateRoots.first())
    }

    fun install(projectRoot: Path, content: String): AgentSkillInstallResult {
        val root = projectRoot.toRealPath()
        val skillRoot = resolveSkillRoot(root)
        require(skillRoot.startsWith(root)) { "Skill directory is outside the project" }
        createSafeDirectories(root, skillRoot)
        val directory = skillRoot.resolve("agent-review-notes")
        createSafeDirectories(root, directory)
        val target = directory.resolve("SKILL.md")
        if (Files.exists(target, NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(target, NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
                "Existing skill target is unsafe"
            }
            val status = if (Files.readString(target) == content) {
                AgentSkillInstallStatus.ALREADY_INSTALLED
            } else {
                AgentSkillInstallStatus.CONFLICT
            }
            return AgentSkillInstallResult(target, status)
        }

        val temporary = Files.createTempFile(directory, ".SKILL-", ".tmp")
        try {
            Files.writeString(temporary, content)
            try {
                Files.move(temporary, target, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return AgentSkillInstallResult(target, AgentSkillInstallStatus.INSTALLED)
    }

    private fun isSafeDirectory(path: Path): Boolean =
        Files.isDirectory(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

    private fun createSafeDirectories(root: Path, target: Path) {
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, NOFOLLOW_LINKS)) {
                require(isSafeDirectory(current)) { "Skill path contains an unsafe component: $current" }
            } else {
                Files.createDirectory(current)
                require(isSafeDirectory(current)) { "Created skill directory is unsafe: $current" }
            }
        }
    }
}
