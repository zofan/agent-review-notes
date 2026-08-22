package ai.agentreviewnotes.marker

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import git4idea.repo.GitRepositoryManager

class ReviewNoteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val first = elements.firstOrNull() ?: return
        val project = first.project
        val psiFile = first.containingFile ?: return
        val basePath = project.basePath ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val filePath = virtualFile.path
        val projectRoot = java.nio.file.Path.of(basePath).normalize()
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile)
        val currentBranch = repository?.currentBranchName
        val currentVcsRoot = repository?.root?.path?.let { rootPath ->
            runCatching { projectRoot.relativize(java.nio.file.Path.of(rootPath).normalize()) }
                .getOrNull()
                ?.toString()
                ?.replace(java.io.File.separatorChar, '/')
        }
        val workspacePath = projectRoot.relativize(java.nio.file.Path.of(filePath).normalize())
            .toString()
            .replace(java.io.File.separatorChar, '/')
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
            val marker = NavigationGutterIconBuilder.create(AllIcons.General.BalloonInformation)
                .setTooltipText("${note.kind.uppercase()}: ${note.message}")
                .setTarget(target)
                .createLineMarkerInfo(target)
            result.add(marker)
        }
    }
}
