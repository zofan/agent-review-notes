package ai.agentreviewnotes.marker

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import ai.agentreviewnotes.ui.ReviewNoteToolWindowService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

internal class ReviewNoteGutterIconRenderer(
    private val project: Project,
    private val note: ReviewNote,
) : GutterIconRenderer() {
    private val tooltip = "${note.kind.uppercase()}: ${note.message}"
    private val presentation = ReviewNotePresentations.forWireValue(note.kind)

    override fun getIcon(): Icon =
        IconLoader.getIcon(presentation.iconPath, ReviewNoteGutterIconRenderer::class.java)

    override fun getTooltipText(): String = tooltip

    override fun getAccessibleName(): String = "Open note: $tooltip"

    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            project.service<ReviewNoteToolWindowService>().showNote(note.id)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ReviewNoteGutterIconRenderer && other.project == project && other.note.id == note.id

    override fun hashCode(): Int = 31 * project.hashCode() + note.id.hashCode()
}
