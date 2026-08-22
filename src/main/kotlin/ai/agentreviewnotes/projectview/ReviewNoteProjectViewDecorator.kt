package ai.agentreviewnotes.projectview

import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

class ReviewNoteProjectViewDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.value as? VirtualFile ?: return
        val project = node.project
        if (project.isDisposed) return
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath)?.canonicalPath?.let(Path::of)
            ?: return
        val filePath = file.canonicalPath?.let(Path::of) ?: return
        val workspacePath = ReviewNotePathPolicy.relativeCanonical(projectRoot, filePath) ?: return
        val notes = project.service<ReviewNoteStore>().cachedList()
        if (!ReviewNotePresence.hasActiveNote(workspacePath, file.isDirectory, notes)) return
        val icon = data.getIcon(false) ?: file.fileType.icon ?: return
        data.setIcon(ReviewNoteBadgeIcon(icon))
    }
}
