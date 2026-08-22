package ai.agentreviewnotes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager

@Service(Service.Level.PROJECT)
class ReviewNoteToolWindowService(private val project: Project) {
    private val selection = ReviewNoteSelectionModel()

    fun showNote(noteId: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            selection.request(noteId)
            return
        }
        toolWindow.activate { selection.request(noteId) }
    }

    fun addSelectionListener(parent: Disposable, listener: (String) -> Unit) {
        val subscription = selection.subscribe(listener)
        Disposer.register(parent) { subscription.close() }
    }

    private companion object {
        const val TOOL_WINDOW_ID = "Agent Review"
    }
}
