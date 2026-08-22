package ai.agentreviewnotes.store

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class AtomicReviewNoteReplace(
    private val move: (Path, Path) -> Unit = { source, target ->
        Files.move(
            source,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        Unit
    },
) {
    fun replace(source: Path, target: Path) {
        move(source, target)
    }
}
