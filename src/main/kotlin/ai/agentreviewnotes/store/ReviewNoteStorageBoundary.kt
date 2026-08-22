package ai.agentreviewnotes.store

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal object ReviewNoteStorageBoundary {
    fun resolve(projectRoot: Path, requested: Path, create: Boolean): Path? {
        val root = projectRoot.toAbsolutePath().normalize()
        val target = requested.toAbsolutePath().normalize()
        require(target.startsWith(root)) { "Каталог заметок выходит за пределы проекта" }
        require(Files.isDirectory(root)) { "Корневой каталог проекта не существует" }

        var current = root
        for (component in root.relativize(target)) {
            current = current.resolve(component)
            if (Files.exists(current, NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current)) { "Симлинк в пути каталога заметок запрещён" }
                require(Files.isDirectory(current, NOFOLLOW_LINKS)) { "Путь каталога заметок занят файлом" }
                continue
            }
            if (!create) return null
            Files.createDirectory(current)
        }

        val realRoot = root.toRealPath()
        val realTarget = current.toRealPath()
        require(realTarget.startsWith(realRoot)) { "Каталог заметок выходит за пределы проекта" }
        return realTarget
    }
}
