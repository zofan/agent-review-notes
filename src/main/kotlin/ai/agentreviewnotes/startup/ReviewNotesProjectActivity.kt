package ai.agentreviewnotes.startup

import ai.agentreviewnotes.marker.ReviewNoteEditorHighlighter
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

class ReviewNotesProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val store = project.service<ReviewNoteStore>()
        val highlighter = ReviewNoteEditorHighlighter(project)
        store.addListener(project) { refreshPresentations(project, highlighter) }
        refreshStore(store)
        val connection = project.messageBus.connect(project)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                highlighter.refreshFile(file)
            }
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    FileDocumentManager.getInstance().getFile(event.document)
                        ?.let(highlighter::refreshAfterDocumentChange)
                }
            },
            project,
        )
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any(::isReviewNoteEvent)) {
                    refreshStore(store)
                }
                if (events.any(::isSourcePathChange)) {
                    highlighter.refreshAll()
                }
            }
        })
        connection.subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                highlighter.refreshAll()
                restartDaemon(project, BRANCH_RESTART_REASON)
            },
        )
    }

    private fun refreshStore(store: ReviewNoteStore) {
        store.refreshAsync()
    }

    private fun refreshPresentations(project: Project, highlighter: ReviewNoteEditorHighlighter) {
        refreshProjectView(project)
        highlighter.refreshAll()
        restartDaemon(project, CACHE_RESTART_REASON)
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

    private fun isSourcePathChange(event: VFileEvent): Boolean =
        event is VFileMoveEvent ||
            (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME)

    private fun isReviewNoteEvent(event: VFileEvent): Boolean =
        event.path.replace('\\', '/').contains("/.idea/agent-review-notes/notes/")

    private companion object {
        const val CACHE_RESTART_REASON = "Agent Review Notes cache changed"
        const val BRANCH_RESTART_REASON = "Agent Review Notes Git branch changed"
    }
}
