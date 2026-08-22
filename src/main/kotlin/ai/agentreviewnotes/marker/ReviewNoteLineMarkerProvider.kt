package ai.agentreviewnotes.marker

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.ui.ReviewNoteToolWindowService
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import git4idea.repo.GitRepositoryManager

class ReviewNoteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val first = elements.firstOrNull() ?: return
        val project = first.project
        val psiFile = first.containingFile ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val filePath = virtualFile.canonicalPath ?: return
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath)?.canonicalPath
            ?.let(java.nio.file.Path::of) ?: return
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile)
        val currentBranch = repository?.currentBranchName
        val currentVcsRoot = repository?.root?.canonicalPath?.let { rootPath ->
            ReviewNotePathPolicy.relativeCanonical(projectRoot, java.nio.file.Path.of(rootPath))
        }
        val workspacePath = ReviewNotePathPolicy.relativeCanonical(projectRoot, java.nio.file.Path.of(filePath)) ?: return
        val store = project.service<ReviewNoteStore>()
        val notes = store.cachedList()
            .filter {
                it.status == ReviewStatus.OPEN.wireValue &&
                    it.location.target != "directory" &&
                    it.location.workspacePath == workspacePath &&
                    ReviewNoteBranch.isVisible(
                        noteBranch = it.location.branch,
                        noteVcsRoot = it.location.vcsRoot,
                        currentBranch = currentBranch,
                        currentVcsRoot = currentVcsRoot,
                    )
            }
        if (notes.isEmpty()) return

        val currentText = psiFile.text
        val currentSha256 = ReviewNoteAnchor.sha256(currentText)

        notes.forEach { note ->
            val anchor = ReviewNoteAnchor.resolve(note, currentText, currentSha256)
            if (anchor is AnchorResult.Unresolved) return@forEach
            val offset = (anchor as AnchorResult.Resolved).offset
            val target = elements.firstOrNull { element -> element.textRange.containsOffset(offset) }
                ?: return@forEach
            val tooltip = "${note.kind.uppercase()}: ${note.message}"
            val marker = LineMarkerInfo(
                target,
                target.textRange,
                AllIcons.General.BalloonInformation,
                { tooltip },
                GutterIconNavigationHandler { _, _ ->
                    project.service<ReviewNoteToolWindowService>().showNote(note.id)
                },
                GutterIconRenderer.Alignment.LEFT,
                { "Open note: $tooltip" },
            )
            result.add(marker)
        }
    }
}
