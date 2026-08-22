package ai.agentreviewnotes.store

import java.nio.file.Files
import java.nio.file.Path

internal object ReviewNoteTargetBoundary {
    fun resolve(projectRoot: Path, target: Path): Path {
        val realRoot = ReviewNotePathPolicy.real(projectRoot)
        require(!Files.isSymbolicLink(target)) { "Символьная ссылка не может быть целью заметки" }
        val realTarget = ReviewNotePathPolicy.real(target)
        require(realTarget.startsWith(realRoot)) { "Цель заметки выходит за реальные пределы проекта" }
        return realTarget
    }
}
