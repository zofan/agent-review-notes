package ai.agentreviewnotes.action

import ai.agentreviewnotes.ui.ReviewNotesHelpDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ReviewNotesHelpAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ReviewNotesHelpDialog(project).show()
    }
}
