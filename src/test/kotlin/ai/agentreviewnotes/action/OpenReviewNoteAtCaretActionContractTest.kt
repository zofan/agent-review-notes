package ai.agentreviewnotes.action

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class OpenReviewNoteAtCaretActionContractTest {
    @Test
    fun `action queries current editor decorations without resolving anchors on EDT`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/action/OpenReviewNoteAtCaretAction.kt"),
        )

        assertContains(source, "ActionUpdateThread.EDT")
        assertContains(source, "selection.hasSelection()")
        assertContains(source, "document.getLineStartOffset")
        assertContains(source, "document.getLineEndOffset")
        assertContains(source, "caretOffset = offset")
        assertContains(source, "target.includeEndPoint")
        assertContains(source, "ReviewNoteEditorMarkup.matchingNoteIds")
        assertContains(source, "showCandidates(notes, editor)")
        assertFalse(source.contains("ReviewNoteAnchor.resolve"))
        assertFalse(source.contains("sha256"))
    }

    @Test
    fun `details service opens one dialog or an accessible chooser for overlaps`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNoteDetailsService.kt"),
        )

        assertContains(source, "0 -> return")
        assertContains(source, "1 -> show(notes.single())")
        assertContains(source, "createPopupChooserBuilder(notes)")
        assertContains(source, "setAccessibleName(\"Review notes at caret\")")
        assertContains(source, "setItemChosenCallback(::show)")
    }
}
