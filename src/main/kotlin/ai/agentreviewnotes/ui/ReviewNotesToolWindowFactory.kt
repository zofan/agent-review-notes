package ai.agentreviewnotes.ui

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor

import ai.agentreviewnotes.model.ReviewNoteCreatedAtFilter
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteKindFilter
import ai.agentreviewnotes.model.ReviewNoteStatusFilter
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import ai.agentreviewnotes.skill.AgentSkillInstallStatus
import ai.agentreviewnotes.skill.AgentSkillInstaller
import ai.agentreviewnotes.skill.BundledReviewSkill
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteRepositoryMapping
import ai.agentreviewnotes.store.ReviewNoteTargetBoundary
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CompletionException
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class ReviewNotesToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewNotesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private data class KindFilterOption(val title: String, val kind: ReviewKind?)

private data class StatusFilterOption(val title: String, val status: ReviewStatus?) {
    override fun toString(): String = title
}

private class ReviewNotesPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val store = project.service<ReviewNoteStore>()
    private val toolWindowService = project.service<ReviewNoteToolWindowService>()
    private val model = DefaultListModel<ReviewNote>()
    private val notes = JBList(model)
    private val kindFilter = ComboBox(
        (listOf(KindFilterOption("All types", null)) +
            ReviewKind.entries.map { kind -> KindFilterOption(kind.title, kind) }).toTypedArray(),
    )
    private val dateFilter = ComboBox(ReviewNoteDateFilterPreset.entries.toTypedArray())
    private val statusFilter = ComboBox(
        (listOf(StatusFilterOption("All statuses", null)) + ReviewStatus.entries.map { status ->
            StatusFilterOption(status.wireValue.replace('_', ' ').replaceFirstChar { it.uppercase() }, status)
        }).toTypedArray(),
    )
    private val branchFilter = ComboBox(arrayOf(ReviewNoteBranchFilter.all))
    private val repositoryFilter = ComboBox(arrayOf(ReviewNoteRepositoryFilter.all))

    @Volatile
    private var disposed = false

    private var updatingFacetFilters = false

    init {
        notes.selectionMode = ListSelectionModel.SINGLE_SELECTION
        notes.cellRenderer = ReviewNoteRenderer()
        notes.addListSelectionListener { updateButtons() }
        ReviewNoteListActivation.install(notes, openDetails = ::viewSelected, navigate = ::navigateToSelected)
        toolWindowService.addSelectionListener(this, ::selectNote)

        add(JBScrollPane(notes), BorderLayout.CENTER)
        add(createToolbar(), BorderLayout.NORTH)
        store.addListener(this) {
            if (isUnavailable()) return@addListener
            ApplicationManager.getApplication().invokeLater {
                if (!isUnavailable()) render(store.cachedList())
            }
        }
        refresh()
    }

    private lateinit var actionsMenu: ReviewNoteActionsMenu
    private lateinit var installSkillButton: JButton

    private fun createToolbar(): JPanel {
        actionsMenu = ReviewNoteActionsMenuFactory.create(
            onEdit = ::editSelected,
            onDelete = ::deleteSelected,
            onResolve = { setSelectedStatus(ReviewStatus.RESOLVED) },
            onReopen = { setSelectedStatus(ReviewStatus.OPEN) },
        )
        ReviewNoteContextMenu.install(notes) { x, y -> actionsMenu.popup.show(notes, x, y) }
        installSkillButton = AgentSkillInstallButtonFactory.create(::installSkill)
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            kindFilter.renderer = KindFilterRenderer()
            kindFilter.toolTipText = "Filter notes by type"
            kindFilter.accessibleContext.accessibleName = "Note type"
            kindFilter.addActionListener { render(store.cachedList()) }
            dateFilter.toolTipText = "Filter notes by creation date"
            dateFilter.accessibleContext.accessibleName = "Creation date"
            dateFilter.addActionListener { render(store.cachedList()) }
            statusFilter.toolTipText = "Filter notes by status"
            statusFilter.accessibleContext.accessibleName = "Note status"
            statusFilter.addActionListener { render(store.cachedList()) }
            branchFilter.toolTipText = "Filter notes by branch"
            branchFilter.accessibleContext.accessibleName = "Note branch"
            branchFilter.addActionListener {
                if (!updatingFacetFilters) render(store.cachedList())
            }
            repositoryFilter.toolTipText = "Filter notes by repository"
            repositoryFilter.accessibleContext.accessibleName = "Note repository"
            repositoryFilter.addActionListener {
                if (!updatingFacetFilters) render(store.cachedList())
            }
            add(ReviewNoteActionButtonFactory.createCompact(AllIcons.Actions.Refresh, "Refresh notes", ::refresh))
            add(kindFilter)
            add(dateFilter)
            add(statusFilter)
            add(branchFilter)
            add(repositoryFilter)
            add(installSkillButton)
        }
    }

    private fun installSkill() {
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showWarningDialog(project, "The project has no local directory", "Agent Review Notes")
            return
        }
        installSkillButton.isEnabled = false
        java.util.concurrent.CompletableFuture.supplyAsync(
            {
                val installResult = AgentSkillInstaller.install(Path.of(basePath), BundledReviewSkill.files())
                val target = runCatching {
                    Path.of(basePath).toRealPath().relativize(installResult.target).toString()
                }.getOrDefault(installResult.target.toString())
                installResult to target
            },
            AppExecutorUtil.getAppExecutorService(),
        ).whenComplete { outcome, error ->
            ApplicationManager.getApplication().invokeLater {
                if (isUnavailable()) return@invokeLater
                installSkillButton.isEnabled = true
                if (error != null) {
                    val cause = (error as? CompletionException)?.cause ?: error
                    Messages.showErrorDialog(
                        project,
                        cause.message ?: "Failed to install the Agent Review Notes skill",
                        "Agent Review Notes",
                    )
                    return@invokeLater
                }
                val (result, target) = outcome
                when (result.status) {
                    AgentSkillInstallStatus.INSTALLED -> Messages.showInfoMessage(
                        project,
                        "Agent Review Notes skill installed at $target",
                        "Agent Review Notes",
                    )
                    AgentSkillInstallStatus.ALREADY_INSTALLED -> Messages.showInfoMessage(
                        project,
                        "Agent Review Notes skill is already installed at $target",
                        "Agent Review Notes",
                    )
                    AgentSkillInstallStatus.CONFLICT -> Messages.showWarningDialog(
                        project,
                        "A different skill already exists at $target. It was not overwritten.",
                        "Agent Review Notes",
                    )
                }
            }
        }
    }

    private fun refresh() {
        store.refreshAsync().whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Failed to refresh notes", error)
        }
    }

    private fun render(loaded: List<ReviewNote>) {
        val selectedId = notes.selectedValue?.id
        updateFacetFilters(loaded)
        model.clear()
        val selectedKind = kindFilter.item.kind
        val selectedStatus = statusFilter.item.status
        val selectedBranch = branchFilter.item
        val selectedRepository = repositoryFilter.item
        val dateBounds = dateFilter.item.bounds(LocalDate.now(), ZoneId.systemDefault())
        loaded.asSequence()
            .filter { note -> ReviewNoteBranchFilter.isVisible(note.location.branch, selectedBranch) }
            .filter { note -> ReviewNoteRepositoryFilter.isVisible(note.location.vcsRoot, selectedRepository) }
            .filter { note -> ReviewNoteKindFilter.isVisible(note.kind, selectedKind) }
            .filter { note -> ReviewNoteStatusFilter.isVisible(note.status, selectedStatus) }
            .filter { note -> ReviewNoteCreatedAtFilter.isVisible(note.createdAt, dateBounds.from, dateBounds.to) }
            .forEach(model::addElement)
        if (selectedId != null) {
            val selectedIndex = (0 until model.size()).firstOrNull { model.getElementAt(it).id == selectedId }
            if (selectedIndex != null) notes.selectedIndex = selectedIndex
        }
        updateButtons()
    }

    private fun updateFacetFilters(loaded: List<ReviewNote>) {
        updatingFacetFilters = true
        try {
            replaceOptions(branchFilter, ReviewNoteBranchFilter.options(loaded), ReviewNoteBranchFilter.all)
            replaceOptions(repositoryFilter, ReviewNoteRepositoryFilter.options(loaded), ReviewNoteRepositoryFilter.all)
        } finally {
            updatingFacetFilters = false
        }
    }

    private fun <T> replaceOptions(comboBox: ComboBox<T>, options: List<T>, fallback: T) {
        val currentOptions = (0 until comboBox.itemCount).map(comboBox::getItemAt)
        if (currentOptions == options) return
        val selected = comboBox.item
        comboBox.removeAllItems()
        options.forEach(comboBox::addItem)
        comboBox.selectedItem = options.firstOrNull { it == selected } ?: fallback
    }


    private fun selectNote(noteId: String) {
        val index = (0 until model.size()).firstOrNull { model.getElementAt(it).id == noteId } ?: return
        notes.selectedIndex = index
        notes.ensureIndexIsVisible(index)
        notes.requestFocusInWindow()
    }


    private fun updateButtons() {
        val selected = notes.selectedValue
        actionsMenu.editItem.isEnabled = selected != null
        actionsMenu.deleteItem.isEnabled = selected != null
        actionsMenu.resolveItem.isEnabled = selected != null && selected.status != ReviewStatus.RESOLVED.wireValue
        actionsMenu.reopenItem.isEnabled = selected != null && selected.status != ReviewStatus.OPEN.wireValue
    }

    private fun viewSelected() {
        val note = notes.selectedValue ?: return
        project.service<ReviewNoteDetailsService>().show(note)
    }

    private fun editSelected() {
        val note = notes.selectedValue ?: return
        project.service<ReviewNoteDetailsService>().show(note)
    }

    private fun deleteSelected() {
        val note = notes.selectedValue ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "Delete note ${note.id}?",
            "Delete Review Note",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return
        store.deleteAsync(note.id).whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Failed to delete the note", error)
        }
    }

    private fun navigateToSelected() {
        val note = notes.selectedValue ?: return
        val repositoryRoots = GitRepositoryManager.getInstance(project).repositories.map { repository ->
            Path.of(repository.root.path)
        }
        java.util.concurrent.CompletableFuture.supplyAsync(
            { resolveNavigation(note, repositoryRoots) },
            AppExecutorUtil.getAppExecutorService(),
        ).whenComplete { outcome, error ->
            if (isUnavailable()) return@whenComplete
            if (error != null) {
                showError("Failed to open the note", error)
                return@whenComplete
            }
            ApplicationManager.getApplication().invokeLater {
                if (!isUnavailable()) applyNavigation(note, outcome)
            }
        }
    }

    private fun setSelectedStatus(status: ReviewStatus) {
        val note = notes.selectedValue ?: return
        store.setStatusAsync(note.id, status).whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Failed to change the note status", error)
        }
    }

    private fun resolveNavigation(note: ReviewNote, repositoryRoots: List<Path>): NavigationOutcome {
        val basePath = project.basePath ?: return NavigationOutcome.Warning("The project has no local directory")
        val projectRoot = Path.of(basePath).normalize()
        val path = projectRoot.resolve(note.location.workspacePath).normalize()
        if (!path.startsWith(projectRoot)) {
            return NavigationOutcome.Warning("The note path is outside the project")
        }
        val mappings = note.location.vcsRoot?.let { vcsRoot ->
            val workspaceRepositoryRoot = projectRoot.resolve(vcsRoot).normalize()
            repositoryRoots.map { repositoryRoot ->
                ReviewNoteRepositoryMapping(workspaceRepositoryRoot, repositoryRoot)
            }
        }.orEmpty()
        val realPath = runCatching { ReviewNoteTargetBoundary.resolveCanonical(projectRoot, path, mappings) }.getOrNull()
            ?: return NavigationOutcome.Warning("The note target no longer exists")
        val file = LocalFileSystem.getInstance().findFileByNioFile(realPath)
            ?: return NavigationOutcome.Warning("The note file no longer exists")
        val document = if (note.location.target == "directory") {
            null
        } else {
            FileDocumentManager.getInstance().getDocument(file)
                ?: return NavigationOutcome.Warning("The note file cannot be opened as text")
        }
        return ReviewNoteReadAction.compute { resolveModelNavigation(note, file, document) }
    }

    private fun resolveModelNavigation(
        note: ReviewNote,
        file: VirtualFile,
        document: Document?,
    ): NavigationOutcome {
        if (note.location.target == "directory") {
            if (!file.isDirectory) {
                return NavigationOutcome.Warning("The note directory no longer exists")
            }
            return NavigationOutcome.Directory(file)
        }
        requireNotNull(document) { "A file navigation document must be prepared before the read action" }
        return when (val anchor = ReviewNoteAnchor.resolve(note, document.immutableCharSequence.toString())) {
            is AnchorResult.Resolved -> NavigationOutcome.Resolved(file, anchor.offset)
            is AnchorResult.Unresolved -> NavigationOutcome.NeedsReanchor(anchor.reason)
        }
    }

    private fun applyNavigation(note: ReviewNote, outcome: NavigationOutcome) {
        when (outcome) {
            is NavigationOutcome.Resolved -> OpenFileDescriptor(project, outcome.file, outcome.offset).navigate(true)
            is NavigationOutcome.Directory -> ProjectView.getInstance(project).select(null, outcome.file, true)
            is NavigationOutcome.NeedsReanchor -> {
                store.setStatusAsync(note.id, ReviewStatus.NEEDS_REANCHOR)
                Messages.showWarningDialog(project, outcome.reason, "Manual Re-anchoring Required")
            }
            is NavigationOutcome.Warning -> Messages.showWarningDialog(project, outcome.message, "Agent Review Notes")
        }
    }

    private fun showError(message: String, error: Throwable) {
        if (isUnavailable()) return
        val cause = (error as? CompletionException)?.cause ?: error
        ApplicationManager.getApplication().invokeLater {
            if (!isUnavailable()) Messages.showErrorDialog(project, cause.message ?: message, "Agent Review Notes")
        }
    }

    private fun isUnavailable(): Boolean = disposed || project.isDisposed

    override fun dispose() {
        disposed = true
    }

    private class KindFilterRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (component is JLabel && value is KindFilterOption) {
                component.text = value.title
                component.icon = value.kind?.let { kind -> ReviewNotePresentations.forKind(kind).icon() }
            }
            return component
        }
    }

    private class ReviewNoteRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (component is JLabel && value is ReviewNote) {
                val row = ReviewNoteListPresentation.row(value)
                component.text = row.text
                component.icon = ReviewNotePresentations.forWireValue(value.kind).icon()
                component.toolTipText = row.toolTip
            }
            return component
        }
    }

    private sealed interface NavigationOutcome {
        data class Resolved(val file: com.intellij.openapi.vfs.VirtualFile, val offset: Int) : NavigationOutcome
        data class Directory(val file: com.intellij.openapi.vfs.VirtualFile) : NavigationOutcome
        data class NeedsReanchor(val reason: String) : NavigationOutcome
        data class Warning(val message: String) : NavigationOutcome
    }
}
