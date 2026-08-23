package ai.agentreviewnotes.store

import java.nio.file.Path

internal data class ReviewNoteRepositoryMapping(
    val workspaceRoot: Path,
    val repositoryRoot: Path,
)

internal object ReviewNoteTargetBoundary {
    fun isWorkspacePath(projectRoot: Path, target: Path): Boolean {
        val lexicalRoot = projectRoot.toAbsolutePath().normalize()
        return target.toAbsolutePath().normalize().startsWith(lexicalRoot)
    }

    fun resolve(
        projectRoot: Path,
        target: Path,
        repositoryMappings: Collection<ReviewNoteRepositoryMapping> = emptyList(),
    ): Path = admit(projectRoot, target, repositoryMappings).lexicalTarget

    fun resolveCanonical(
        projectRoot: Path,
        target: Path,
        repositoryMappings: Collection<ReviewNoteRepositoryMapping> = emptyList(),
    ): Path = admit(projectRoot, target, repositoryMappings).canonicalTarget

    private fun admit(
        projectRoot: Path,
        target: Path,
        repositoryMappings: Collection<ReviewNoteRepositoryMapping>,
    ): AdmittedTarget {
        val lexicalRoot = projectRoot.toAbsolutePath().normalize()
        val lexicalTarget = target.toAbsolutePath().normalize()
        require(isWorkspacePath(lexicalRoot, lexicalTarget)) { "Цель заметки выходит за пределы workspace" }

        val realProjectRoot = ReviewNotePathPolicy.real(lexicalRoot)
        val realTarget = ReviewNotePathPolicy.real(lexicalTarget)
        if (realTarget.startsWith(realProjectRoot)) return AdmittedTarget(lexicalTarget, realTarget)

        val admittedByRepository = repositoryMappings.any { mapping ->
            val workspaceRepositoryRoot = mapping.workspaceRoot.toAbsolutePath().normalize()
            if (!workspaceRepositoryRoot.startsWith(lexicalRoot) || !lexicalTarget.startsWith(workspaceRepositoryRoot)) {
                return@any false
            }
            val realRepositoryRoot = runCatching { ReviewNotePathPolicy.real(mapping.repositoryRoot) }.getOrNull()
                ?: return@any false
            val realWorkspaceRepositoryRoot = runCatching { ReviewNotePathPolicy.real(workspaceRepositoryRoot) }.getOrNull()
                ?: return@any false
            realWorkspaceRepositoryRoot == realRepositoryRoot && realTarget.startsWith(realRepositoryRoot)
        }
        require(admittedByRepository) { "Цель заметки выходит за пределы зарегистрированного репозитория" }
        return AdmittedTarget(lexicalTarget, realTarget)
    }

    private data class AdmittedTarget(
        val lexicalTarget: Path,
        val canonicalTarget: Path,
    )
}
