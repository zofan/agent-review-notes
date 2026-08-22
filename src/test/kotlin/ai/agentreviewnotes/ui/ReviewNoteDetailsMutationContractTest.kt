package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReviewNoteDetailsMutationContractTest {
    @Test
    fun `details dialog edits type status and note inline`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsDialog.kt"),
        )

        assertContains(source, "beginInlineEdit(ReviewNoteInlineField.TYPE)")
        assertContains(source, "beginInlineEdit(ReviewNoteInlineField.STATUS)")
        assertContains(source, "beginInlineEdit(ReviewNoteInlineField.NOTE)")
        assertContains(source, "saveKind()")
        assertContains(source, "saveStatus()")
        assertContains(source, "saveMessage()")
        assertContains(source, "event.clickCount == 2")
        assertContains(source, "Save")
        assertContains(source, "Cancel")
        assertContains(source, "Delete…")
        assertContains(source, "closeAction")
        assertFalse(source.contains("Edit Type & Text…"))
        assertFalse(source.contains("Change Status…"))
    }

    @Test
    fun `inline mutation completion updates UI on EDT without closing details`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsDialog.kt"),
        )

        assertContains(source, "ApplicationManager.getApplication().invokeLater")
        assertContains(source, "state = state.succeeded()")
        assertContains(source, "state = state.failed()")
        assertContains(source, "if (project.isDisposed || !isShowing)")
        assertContains(source, "setMutationControlsEnabled")
        assertContains(source, "inlineMutationButtons")
        assertContains(source, "operation: () -> CompletableFuture<*>")
        assertContains(source, "if (state.pending || state.activeField != field) return")
        assertFalse(source.contains("if (mutation()) close"))
    }

    @Test
    fun `details service exposes canonical futures to inline controls`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsService.kt"),
        )

        assertContains(source, "ApplicationManager.getApplication().invokeLater")
        assertContains(source, "if (project.isDisposed) return@invokeLater")
        assertContains(source, "onUpdate = { kind, message -> store.updateAsync(note.id, kind, message) }")
        assertContains(source, "onChangeStatus = { status -> store.setStatusAsync(note.id, status) }")
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
    }
}
