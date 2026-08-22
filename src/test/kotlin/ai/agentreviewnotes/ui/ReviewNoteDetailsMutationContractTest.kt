package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNoteDetailsMutationContractTest {
    @Test
    fun `one edit action switches the complete details form into edit mode`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsDialog.kt"),
        )

        assertContains(source, "beginEditing()")
        assertContains(source, "saveChanges()")
        assertContains(source, "cancelEditing()")
        assertContains(source, "ReviewNoteStatusChoices.all")
        assertContains(source, "onSave: (ReviewKind, ReviewStatus, String) -> CompletableFuture<*>")
        assertContains(source, "Save")
        assertContains(source, "Cancel")
        assertContains(source, "Delete…")
        assertContains(source, "closeAction")
        assertFalse(source.contains("typeEditButton"))
        assertFalse(source.contains("statusEditButton"))
        assertFalse(source.contains("noteEditButton"))
    }

    @Test
    fun `inline mutation completion updates UI on EDT without closing details`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsDialog.kt"),
        )

        assertContains(source, "ApplicationManager.getApplication().invokeLater")
        assertContains(source, "ModalityState.stateForComponent(rootPane)")
        assertContains(source, "}, completionModality)")
        assertContains(source, "state = state.succeeded()")
        assertContains(source, "state = state.failed()")
        assertContains(source, "if (project.isDisposed || !isShowing)")
        assertContains(source, "setMutationControlsEnabled")
        assertContains(source, "inlineMutationButtons")
        assertContains(source, "operation: () -> CompletableFuture<*>")
        assertContains(source, "if (state.pending) return")
        assertFalse(source.contains("if (mutation()) close"))
    }

    @Test
    fun `details service exposes canonical futures to inline controls`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsService.kt"),
        )

        assertContains(source, "ApplicationManager.getApplication().invokeLater")
        assertContains(source, "if (project.isDisposed) return@invokeLater")
        assertContains(source, "onSave = { kind, status, message -> store.updateAsync(note.id, kind, status, message) }")
        assertContains(source, "onDelete = { store.deleteAsync(note.id) }")
        assertFalse(source.contains("ReviewNoteDialog(project"))
        assertFalse(source.contains("ReviewNoteStatusDialog(project"))
    }

    @Test
    fun `tool window uses the shared details mutation service`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNotesToolWindowFactory.kt"),
        )

        assertContains(source, "project.service<ReviewNoteDetailsService>().show(note)")
        assertFalse(source.contains("ReviewNoteDialog(project, initialKind"))
    }
}
