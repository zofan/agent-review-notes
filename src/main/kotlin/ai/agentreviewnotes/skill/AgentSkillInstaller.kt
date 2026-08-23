package ai.agentreviewnotes.skill

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

private data class DirectoryIdentity(val realPath: Path, val fileKey: Any)

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
        ".codex/skills",
        ".opencode/skills",
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
        return install(projectRoot, mapOf("SKILL.md" to content))
    }

    fun install(projectRoot: Path, files: Map<String, String>): AgentSkillInstallResult {
        return install(projectRoot, files) {}
    }

    internal fun install(
        projectRoot: Path,
        files: Map<String, String>,
        beforePublish: (Path) -> Unit,
    ): AgentSkillInstallResult {
        require(files.keys.contains("SKILL.md")) { "Skill package must contain SKILL.md" }
        require(files.isNotEmpty()) { "Skill package must not be empty" }
        val root = projectRoot.toRealPath()
        val skillRoot = resolveSkillRoot(root)
        require(skillRoot.startsWith(root)) { "Skill directory is outside the project" }
        createSafeDirectories(root, skillRoot)
        val directory = skillRoot.resolve("agent-review-notes")
        val target = directory.resolve("SKILL.md")
        val packageFiles = files.map { (relativeName, content) ->
            requireSafeRelativePath(relativeName) to content
        }
        require(packageFiles.map { it.first }.toSet().size == packageFiles.size) {
            "Skill package contains duplicate canonical paths"
        }

        if (Files.exists(directory, NOFOLLOW_LINKS)) {
            require(isSafeDirectory(directory)) { "Existing skill package is unsafe: $directory" }
            val completeMatch = packageMatchesExactly(directory, packageFiles)
            return AgentSkillInstallResult(
                target,
                if (completeMatch) AgentSkillInstallStatus.ALREADY_INSTALLED else AgentSkillInstallStatus.CONFLICT,
            )
        }

        val rootIdentity = directoryIdentity(skillRoot)
        val staging = skillRoot.resolve(".agent-review-notes-${UUID.randomUUID()}.tmp")
        var stagingIdentity: DirectoryIdentity? = null
        try {
            stagingIdentity = createPrivateDirectory(staging)
            requireSameDirectory(skillRoot, rootIdentity)
            packageFiles.forEach { (relative, content) ->
                val file = staging.resolve(relative)
                createSafeDirectories(staging, file.parent)
                writeNewFile(file, content)
            }
            require(packageMatchesExactly(staging, packageFiles)) {
                "Prepared skill package manifest changed before publication"
            }
            beforePublish(directory)
            publishPackage(skillRoot, rootIdentity, staging, directory, packageFiles)
        } catch (error: Throwable) {
            stagingIdentity?.let { identity ->
                runCatching { cleanupUnpublishedStaging(staging, identity) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            throw error
        }
        return AgentSkillInstallResult(target, AgentSkillInstallStatus.INSTALLED)
    }

    private fun publishPackage(
        skillRoot: Path,
        expectedRoot: DirectoryIdentity,
        staging: Path,
        target: Path,
        expectedFiles: List<Pair<Path, String>>,
    ) {
        requireSameDirectory(skillRoot, expectedRoot)
        require(packageMatchesExactly(staging, expectedFiles)) {
            "Prepared skill package manifest changed before publication"
        }
        Files.move(staging, target)
        requireSameDirectory(skillRoot, expectedRoot)
        require(packageMatchesExactly(target, expectedFiles)) {
            "Published skill package manifest changed concurrently"
        }
    }

    private fun cleanupUnpublishedStaging(staging: Path, expected: DirectoryIdentity) {
        if (!Files.exists(staging, NOFOLLOW_LINKS)) return
        require(directoryIdentity(staging) == expected) {
            "Refusing to clean a replaced staging directory: $staging"
        }
        Files.walk(staging).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    private fun packageMatchesExactly(root: Path, expectedFiles: List<Pair<Path, String>>): Boolean = runCatching {
        val expectedByPath = expectedFiles.toMap()
        val expectedDirectories = buildSet {
            expectedByPath.keys.forEach { relative ->
                var parent = relative.parent
                while (parent != null) {
                    add(parent)
                    parent = parent.parent
                }
            }
        }
        val actualFiles = mutableSetOf<Path>()
        val actualDirectories = mutableSetOf<Path>()
        Files.walk(root).use { paths ->
            paths.forEach { entry ->
                if (entry == root) return@forEach
                val relative = root.relativize(entry)
                require(!Files.isSymbolicLink(entry))
                when {
                    Files.isDirectory(entry, NOFOLLOW_LINKS) -> actualDirectories.add(relative)
                    Files.isRegularFile(entry, NOFOLLOW_LINKS) -> {
                        require(expectedByPath[relative] == Files.readString(entry))
                        actualFiles.add(relative)
                    }
                    else -> error("Unsupported skill package entry: $entry")
                }
            }
        }
        actualFiles == expectedByPath.keys && actualDirectories == expectedDirectories
    }.getOrDefault(false)

    private fun createPrivateDirectory(target: Path): DirectoryIdentity {
        var created = false
        try {
            if (target.fileSystem.supportedFileAttributeViews().contains("posix")) {
                val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
                Files.createDirectory(target, permissions)
                created = true
                requireNotNull(Files.getFileAttributeView(target, PosixFileAttributeView::class.java, NOFOLLOW_LINKS))
            } else {
                Files.createDirectory(target)
                created = true
            }
            return directoryIdentity(target)
        } catch (error: Throwable) {
            if (created) {
                runCatching { Files.deleteIfExists(target) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            throw error
        }
    }

    private fun writeNewFile(target: Path, content: String) {
        val bytes = ByteBuffer.wrap(content.toByteArray(StandardCharsets.UTF_8))
        Files.newByteChannel(target, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS)).use { channel ->
            while (bytes.hasRemaining()) channel.write(bytes)
        }
    }

    private fun requireSafeRelativePath(value: String): Path {
        val relative = Path.of(value)
        val canonicalSpelling = relative.toString().replace(File.separatorChar, '/')
        require(
            '\\' !in value &&
                value == canonicalSpelling &&
                !relative.isAbsolute &&
                relative.normalize() == relative &&
                relative.none { it.toString() == ".." },
        ) {
            "Unsafe skill package path: $value"
        }
        require(relative.toString() == "SKILL.md" || relative.nameCount >= 2 && relative.first().toString() in safeSubdirectories) {
            "Unsupported skill package path: $value"
        }
        return relative
    }

    private fun directoryIdentity(path: Path): DirectoryIdentity {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(attributes.isDirectory) { "Skill parent is not a directory: $path" }
        return DirectoryIdentity(path.toRealPath(NOFOLLOW_LINKS), requireNotNull(attributes.fileKey()) {
            "Skill filesystem does not expose stable directory identity: $path"
        })
    }

    private fun requireSameDirectory(path: Path, expected: DirectoryIdentity) {
        require(directoryIdentity(path) == expected) { "Skill parent directory changed concurrently: $path" }
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

    private val safeSubdirectories = setOf("assets", "references", "scripts", "templates")
}
