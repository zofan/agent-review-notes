package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import ai.agentreviewnotes.store.ReviewNoteStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

@Service(Service.Level.PROJECT)
class ReviewNoteDetailsService(private val project: Project) {
    private val store: ReviewNoteStore
        get() = project.service()

    fun show(note: ReviewNote) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ReviewNoteDetailsDialog(
                project = project,
                note = note,
                availableParents = store.cachedList().filterNot { it.id == note.id },
                onSave = { kind, status, message, tags, dependsOn ->
                    store.updateAsync(note, kind, status, message, tags, dependsOn)
                },
                onDelete = { store.deleteAsync(note.id) },
            ).show()
        }
    }

    fun showCandidates(notes: List<ReviewNote>, editor: Editor) {
        when (notes.size) {
            0 -> return
            1 -> show(notes.single())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(notes)
                .setTitle("Review Notes at Caret")
                .setRenderer(ReviewNoteCandidateRenderer())
                .setAccessibleName("Review notes at caret")
                .setItemChosenCallback(::show)
                .createPopup()
                .showInBestPositionFor(editor)
        }
    }

    private class ReviewNoteCandidateRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val note = value as? ReviewNote ?: return this
            val kind = note.kind.replaceFirstChar { it.uppercase() }
            val message = note.message.trim().replace(Regex("\\s+"), " ")
            text = "$kind · ${message.take(MAX_PREVIEW_CHARS)}${if (message.length > MAX_PREVIEW_CHARS) "…" else ""}"
            icon = ReviewNotePresentations.forWireValue(note.kind).icon()
            accessibleContext.accessibleName = text
            return this
        }
    }

    private companion object {
        const val MAX_PREVIEW_CHARS = 96
    }
}
