package ai.agentreviewnotes.startup

import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

class ReviewNotesProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val store = project.service<ReviewNoteStore>()
        store.addListener(project) { refreshProjectView(project) }
        refreshAndRestart(project, store)
        val connection = project.messageBus.connect(project)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any(::isReviewNoteEvent)) {
                    refreshAndRestart(project, store)
                }
            }
        })
        connection.subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                restartDaemon(project, BRANCH_RESTART_REASON)
            },
        )
    }

    private fun refreshAndRestart(project: Project, store: ReviewNoteStore) {
        store.refreshAsync().thenRun {
            restartDaemon(project, CACHE_RESTART_REASON)
        }
    }

    private fun refreshProjectView(project: Project) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) ProjectView.getInstance(project).refresh()
        }
    }

    private fun restartDaemon(project: Project, reason: String) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart(reason)
            }
        }
    }

    private fun isReviewNoteEvent(event: VFileEvent): Boolean =
        event.path.replace('\\', '/').contains("/.idea/agent-review-notes/notes/")

    private companion object {
        const val CACHE_RESTART_REASON = "Agent Review Notes cache changed"
        const val BRANCH_RESTART_REASON = "Agent Review Notes Git branch changed"
    }
}
