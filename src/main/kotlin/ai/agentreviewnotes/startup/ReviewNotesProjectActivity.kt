package ai.agentreviewnotes.startup

import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class ReviewNotesProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val store = project.service<ReviewNoteStore>()
        refreshAndRestart(project, store)
        val connection = project.messageBus.connect(project)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any(::isReviewNoteEvent)) {
                    refreshAndRestart(project, store)
                }
            }
        })
    }

    private fun refreshAndRestart(project: Project, store: ReviewNoteStore) {
        store.refreshAsync().thenRun {
            if (project.isDisposed) return@thenRun
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    DaemonCodeAnalyzer.getInstance(project).restart(RESTART_REASON)
                }
            }
        }
    }

    private fun isReviewNoteEvent(event: VFileEvent): Boolean =
        event.path.replace('\\', '/').contains("/.idea/agent-review-notes/notes/")

    private companion object {
        const val RESTART_REASON = "Agent Review Notes cache changed"
    }
}
