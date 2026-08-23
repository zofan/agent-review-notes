package ai.agentreviewnotes.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNotesToolbarContractTest {
    @Test
    fun `панель не содержит кнопок деталей навигации и действий над заметкой`() {
        val source = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/kotlin/ai/agentreviewnotes/ui/ReviewNotesToolWindowFactory.kt"),
        )

        assertFalse(source.contains("viewButton"))
        assertFalse(source.contains("navigateButton"))
        assertFalse(source.contains("actionsMenu.button"))
        assertFalse(source.contains("JLabel(\"Type:\")"))
        assertFalse(source.contains("add(kindLabel)"))
        assertTrue(source.contains("add(kindFilter)"))
        assertTrue(source.contains("add(branchFilter)"))
        assertTrue(source.contains("add(repositoryFilter)"))
        assertTrue(source.contains("Filter notes by branch"))
        assertTrue(source.contains("Filter notes by repository"))
        assertTrue(source.contains("ReviewNoteListPresentation.row(value)"))
        assertTrue(source.contains("ReviewNoteBranchFilter.isVisible"))
        assertTrue(source.contains("ReviewNoteRepositoryFilter.isVisible"))
        assertFalse(source.contains("private fun isVisibleOnCurrentBranch"))
        assertTrue(source.contains("ReviewNoteRepositoryMapping(workspaceRepositoryRoot, repositoryRoot)"))
        assertTrue(source.contains("ReviewNoteActionButtonFactory.createCompact"))
        assertTrue(source.contains("ReviewNoteContextMenu.install(notes"))

        val installSkill = source.substringAfter("private fun installSkill()")
            .substringBefore("private fun refresh()")
        val backgroundInstall = installSkill.substringBefore(".whenComplete")
        val edtCompletion = installSkill.substringAfter("invokeLater {")
        assertTrue(backgroundInstall.contains("toRealPath()"))
        assertFalse(edtCompletion.contains("toRealPath()"))

        val navigation = source.substringAfter("private fun resolveNavigation")
            .substringBefore("private fun applyNavigation")
        assertTrue(navigation.contains("ReviewNoteTargetBoundary.resolveCanonical"))
        assertTrue(navigation.indexOf("findFileByNioFile") < navigation.indexOf("ReviewNoteReadAction.compute"))
        assertTrue(navigation.indexOf("getDocument(file)") < navigation.indexOf("ReviewNoteReadAction.compute"))
        val modelNavigation = navigation.substringAfter("private fun resolveModelNavigation")
        assertFalse(modelNavigation.contains("findFileByNioFile"))
        assertFalse(modelNavigation.contains("getDocument(file)"))
    }
}
