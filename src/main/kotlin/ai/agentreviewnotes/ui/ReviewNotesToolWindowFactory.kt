package ai.agentreviewnotes.ui

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewNoteCreatedAtFilter
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteKindFilter
import ai.agentreviewnotes.model.ReviewStatus
import ai.agentreviewnotes.skill.AgentSkillInstallStatus
import ai.agentreviewnotes.skill.AgentSkillInstaller
import ai.agentreviewnotes.skill.BundledReviewSkill
import ai.agentreviewnotes.store.ReviewNoteStore
import ai.agentreviewnotes.store.ReviewNotePathPolicy
import ai.agentreviewnotes.store.ReviewNoteTargetBoundary
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.CompletionException
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SpinnerDateModel

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

private class ReviewNotesPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val store = project.service<ReviewNoteStore>()
    private val toolWindowService = project.service<ReviewNoteToolWindowService>()
    private val model = DefaultListModel<ReviewNote>()
    private val notes = JBList(model)
    private val kindFilter = ComboBox(
        (listOf(KindFilterOption("All types", null)) +
            ReviewKind.entries.map { kind -> KindFilterOption(kind.title, kind) }).toTypedArray(),
    )
    private val fromEnabled = JCheckBox("From:")
    private val fromDate = JSpinner(SpinnerDateModel()).apply { editor = JSpinner.DateEditor(this, "yyyy-MM-dd") }
    private val toEnabled = JCheckBox("To:")
    private val toDate = JSpinner(SpinnerDateModel()).apply { editor = JSpinner.DateEditor(this, "yyyy-MM-dd") }

    @Volatile
    private var disposed = false

    init {
        notes.selectionMode = ListSelectionModel.SINGLE_SELECTION
        notes.cellRenderer = ReviewNoteRenderer()
        notes.addListSelectionListener { updateButtons() }
        ReviewNoteListActivation.install(notes, ::navigateToSelected)
        toolWindowService.addSelectionListener(this, ::selectNote)

        add(JBScrollPane(notes), BorderLayout.CENTER)
        add(createToolbar(), BorderLayout.NORTH)
        store.addListener(this) {
            if (isUnavailable()) return@addListener
            ApplicationManager.getApplication().invokeLater {
                if (!isUnavailable()) render(store.cachedList())
            }
        }
        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                if (isUnavailable()) return@GitRepositoryChangeListener
                ApplicationManager.getApplication().invokeLater {
                    if (!isUnavailable()) render(store.cachedList())
                }
            },
        )
        refresh()
    }

    private lateinit var viewButton: JButton
    private lateinit var navigateButton: JButton
    private lateinit var editButton: JButton
    private lateinit var deleteButton: JButton
    private lateinit var resolveButton: JButton
    private lateinit var reopenButton: JButton
    private lateinit var installSkillButton: JButton

    private fun createToolbar(): JPanel {
        viewButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Preview, "View note", ::viewSelected)
        navigateButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Forward, "Go to note target", ::navigateToSelected)
        editButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Edit, "Edit note", ::editSelected)
        deleteButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.DeleteTag, "Delete note", ::deleteSelected)
        resolveButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Checked, "Resolve note") {
            setSelectedStatus(ReviewStatus.RESOLVED)
        }
        reopenButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Rollback, "Reopen note") {
            setSelectedStatus(ReviewStatus.OPEN)
        }
        installSkillButton = AgentSkillInstallButtonFactory.create(::installSkill)
        fromDate.isEnabled = false
        toDate.isEnabled = false
        fromDate.accessibleContext.accessibleName = "Start date"
        toDate.accessibleContext.accessibleName = "End date"
        fromEnabled.addActionListener {
            fromDate.isEnabled = fromEnabled.isSelected
            render(store.cachedList())
        }
        toEnabled.addActionListener {
            toDate.isEnabled = toEnabled.isSelected
            render(store.cachedList())
        }
        fromDate.addChangeListener { if (fromEnabled.isSelected) render(store.cachedList()) }
        toDate.addChangeListener { if (toEnabled.isSelected) render(store.cachedList()) }
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            kindFilter.renderer = KindFilterRenderer()
            kindFilter.toolTipText = "Filter notes by type"
            kindFilter.accessibleContext.accessibleName = "Note type"
            kindFilter.addActionListener { render(store.cachedList()) }
            val kindLabel = JLabel("Type:")
            kindLabel.labelFor = kindFilter
            add(ReviewNoteActionButtonFactory.create(AllIcons.Actions.Refresh, "Refresh notes", ::refresh))
            add(kindLabel)
            add(kindFilter)
            add(fromEnabled)
            add(fromDate)
            add(toEnabled)
            add(toDate)
            add(viewButton)
            add(navigateButton)
            add(editButton)
            add(deleteButton)
            add(resolveButton)
            add(reopenButton)
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
                AgentSkillInstaller.install(Path.of(basePath), BundledReviewSkill.content())
            },
            AppExecutorUtil.getAppExecutorService(),
        ).whenComplete { result, error ->
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
                val target = runCatching {
                    Path.of(basePath).toRealPath().relativize(result.target).toString()
                }.getOrDefault(result.target.toString())
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
        model.clear()
        val selectedKind = kindFilter.item.kind
        val selectedFrom = selectedFrom()
        val selectedTo = selectedTo()
        loaded.asSequence()
            .filter(::isVisibleOnCurrentBranch)
            .filter { note -> ReviewNoteKindFilter.isVisible(note.kind, selectedKind) }
            .filter { note -> ReviewNoteCreatedAtFilter.isVisible(note.createdAt, selectedFrom, selectedTo) }
            .forEach(model::addElement)
        if (selectedId != null) {
            val selectedIndex = (0 until model.size()).firstOrNull { model.getElementAt(it).id == selectedId }
            if (selectedIndex != null) notes.selectedIndex = selectedIndex
        }
        updateButtons()
    }

    private fun selectedFrom(): Instant? {
        if (!fromEnabled.isSelected) return null
        val date = (fromDate.value as Date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant()
    }

    private fun selectedTo(): Instant? {
        if (!toEnabled.isSelected) return null
        val date = (toDate.value as Date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1)
    }

    private fun selectNote(noteId: String) {
        val index = (0 until model.size()).firstOrNull { model.getElementAt(it).id == noteId } ?: return
        notes.selectedIndex = index
        notes.ensureIndexIsVisible(index)
        notes.requestFocusInWindow()
    }

    private fun isVisibleOnCurrentBranch(note: ReviewNote): Boolean {
        val branch = note.location.branch ?: return true
        val vcsRoot = note.location.vcsRoot ?: return false
        val basePath = project.basePath ?: return false
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath)?.canonicalPath?.let(Path::of)
            ?: return false
        val expectedRoot = projectRoot.resolve(vcsRoot).normalize()
        if (!expectedRoot.startsWith(projectRoot)) return false
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull { candidate ->
            candidate.root.canonicalPath?.let(Path::of)?.normalize() == expectedRoot
        }
        val currentVcsRoot = repository?.root?.canonicalPath?.let { rootPath ->
            ReviewNotePathPolicy.relativeCanonical(projectRoot, Path.of(rootPath))
        }
        return ReviewNoteBranch.isVisible(
            noteBranch = branch,
            noteVcsRoot = vcsRoot,
            currentBranch = repository?.currentBranchName,
            currentVcsRoot = currentVcsRoot,
        )
    }

    private fun updateButtons() {
        val selected = notes.selectedValue
        viewButton.isEnabled = selected != null
        navigateButton.isEnabled = selected != null
        editButton.isEnabled = selected != null
        deleteButton.isEnabled = selected != null
        resolveButton.isEnabled = selected != null && selected.status != ReviewStatus.RESOLVED.wireValue
        reopenButton.isEnabled = selected != null && selected.status != ReviewStatus.OPEN.wireValue
    }

    private fun viewSelected() {
        val note = notes.selectedValue ?: return
        ReviewNoteDetailsDialog(project, note).show()
    }

    private fun editSelected() {
        val note = notes.selectedValue ?: return
        val kind = ReviewKind.entries.first { it.wireValue == note.kind }
        val dialog = ReviewNoteDialog(project, initialKind = kind, initialMessage = note.message)
        if (!dialog.showAndGet()) return
        store.updateAsync(note.id, dialog.kind, dialog.message).whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Failed to edit the note", error)
        }
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
        java.util.concurrent.CompletableFuture.supplyAsync(
            { resolveNavigation(note) },
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

    private fun resolveNavigation(note: ReviewNote): NavigationOutcome {
        val basePath = project.basePath ?: return NavigationOutcome.Warning("The project has no local directory")
        val projectRoot = Path.of(basePath).normalize()
        val path = projectRoot.resolve(note.location.workspacePath).normalize()
        if (!path.startsWith(projectRoot)) {
            return NavigationOutcome.Warning("The note path is outside the project")
        }
        val realPath = runCatching { ReviewNoteTargetBoundary.resolve(projectRoot, path) }.getOrNull()
            ?: return NavigationOutcome.Warning("The note target no longer exists")
        return ReviewNoteReadAction.compute { resolveModelNavigation(note, realPath) }
    }

    private fun resolveModelNavigation(note: ReviewNote, realPath: Path): NavigationOutcome {
        val file = LocalFileSystem.getInstance().findFileByNioFile(realPath)
            ?: return NavigationOutcome.Warning("The note file no longer exists")
        if (file.`is`(VFileProperty.SYMLINK)) {
            return NavigationOutcome.Warning("A symbolic link cannot be a note target")
        }
        if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
            return NavigationOutcome.Warning("The note file is outside the project content roots")
        }
        if (note.location.target == "directory") {
            if (!file.isDirectory || file.`is`(VFileProperty.SYMLINK)) {
                return NavigationOutcome.Warning("The note directory no longer exists or is unsafe")
            }
            return NavigationOutcome.Directory(file)
        }
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return NavigationOutcome.Warning("The note file cannot be opened as text")
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
            if (component is JLabel && value is KindFilterOption) component.text = value.title
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
                val symbol = value.anchor.symbol?.let { " · $it" }.orEmpty()
                val branch = value.location.branch?.let { " · $it" }.orEmpty()
                val location = if (value.location.target == "directory") {
                    "${value.location.workspacePath}/"
                } else {
                    "${value.location.workspacePath}:${value.location.startLine}"
                }
                component.text = "${value.kind.uppercase()} · ${value.status}$branch · $location$symbol — ${value.message}"
                component.toolTipText = value.message
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
