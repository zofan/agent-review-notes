package ai.agentreviewnotes.store

import java.nio.file.Path

internal object ReviewNotePathPolicy {
    fun real(path: Path): Path = path.toRealPath()

    fun relative(projectRoot: Path, target: Path): String? {
        val realRoot = real(projectRoot)
        val realTarget = real(target)
        return relativeCanonical(realRoot, realTarget)
    }

    fun relativeCanonical(projectRoot: Path, target: Path): String? {
        val root = projectRoot.normalize()
        val child = target.normalize()
        if (!child.startsWith(root)) return null
        return root.relativize(child).toString().replace(java.io.File.separatorChar, '/')
    }
}
