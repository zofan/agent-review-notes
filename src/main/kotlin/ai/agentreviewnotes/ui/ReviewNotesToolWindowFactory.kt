package ai.agentreviewnotes.ui

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.ReviewNoteBranch
import ai.agentreviewnotes.model.ReviewKind
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.model.ReviewNoteKindFilter
import ai.agentreviewnotes.model.ReviewStatus
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

private class ReviewNotesPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val store = project.service<ReviewNoteStore>()
    private val toolWindowService = project.service<ReviewNoteToolWindowService>()
    private val model = DefaultListModel<ReviewNote>()
    private val notes = JBList(model)
    private val kindFilter = ComboBox(
        (listOf(KindFilterOption("Все типы", null)) +
            ReviewKind.entries.map { kind -> KindFilterOption(kind.title, kind) }).toTypedArray(),
    )

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

    private lateinit var navigateButton: JButton
    private lateinit var editButton: JButton
    private lateinit var deleteButton: JButton
    private lateinit var resolveButton: JButton
    private lateinit var reopenButton: JButton

    private fun createToolbar(): JPanel {
        navigateButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Preview, "Перейти к заметке", ::navigateToSelected)
        editButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Edit, "Изменить заметку", ::editSelected)
        deleteButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.DeleteTag, "Удалить заметку", ::deleteSelected)
        resolveButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Checked, "Отметить решённой") {
            setSelectedStatus(ReviewStatus.RESOLVED)
        }
        reopenButton = ReviewNoteActionButtonFactory.create(AllIcons.Actions.Rollback, "Открыть снова") {
            setSelectedStatus(ReviewStatus.OPEN)
        }
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            kindFilter.renderer = KindFilterRenderer()
            kindFilter.toolTipText = "Фильтр замечаний по типу"
            kindFilter.accessibleContext.accessibleName = "Тип замечания"
            kindFilter.addActionListener { render(store.cachedList()) }
            val kindLabel = JLabel("Тип:")
            kindLabel.labelFor = kindFilter
            add(ReviewNoteActionButtonFactory.create(AllIcons.Actions.Refresh, "Обновить заметки", ::refresh))
            add(kindLabel)
            add(kindFilter)
            add(navigateButton)
            add(editButton)
            add(deleteButton)
            add(resolveButton)
            add(reopenButton)
        }
    }

    private fun refresh() {
        store.refreshAsync().whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Не удалось обновить заметки", error)
        }
    }

    private fun render(loaded: List<ReviewNote>) {
        val selectedId = notes.selectedValue?.id
        model.clear()
        val selectedKind = kindFilter.item.kind
        loaded.asSequence()
            .filter(::isVisibleOnCurrentBranch)
            .filter { note -> ReviewNoteKindFilter.isVisible(note.kind, selectedKind) }
            .forEach(model::addElement)
        if (selectedId != null) {
            val selectedIndex = (0 until model.size()).firstOrNull { model.getElementAt(it).id == selectedId }
            if (selectedIndex != null) notes.selectedIndex = selectedIndex
        }
        updateButtons()
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
        navigateButton.isEnabled = selected != null
        editButton.isEnabled = selected != null
        deleteButton.isEnabled = selected != null
        resolveButton.isEnabled = selected != null && selected.status != ReviewStatus.RESOLVED.wireValue
        reopenButton.isEnabled = selected != null && selected.status != ReviewStatus.OPEN.wireValue
    }

    private fun editSelected() {
        val note = notes.selectedValue ?: return
        val kind = ReviewKind.entries.first { it.wireValue == note.kind }
        val dialog = ReviewNoteDialog(project, initialKind = kind, initialMessage = note.message)
        if (!dialog.showAndGet()) return
        store.updateAsync(note.id, dialog.kind, dialog.message).whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Не удалось изменить заметку", error)
        }
    }

    private fun deleteSelected() {
        val note = notes.selectedValue ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "Удалить замечание ${note.id}?",
            "Удалить замечание",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return
        store.deleteAsync(note.id).whenComplete { _, error ->
            if (!isUnavailable() && error != null) showError("Не удалось удалить заметку", error)
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
                showError("Не удалось открыть заметку", error)
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
            if (!isUnavailable() && error != null) showError("Не удалось изменить статус", error)
        }
    }

    private fun resolveNavigation(note: ReviewNote): NavigationOutcome {
        val basePath = project.basePath ?: return NavigationOutcome.Warning("У проекта нет локального каталога")
        val projectRoot = Path.of(basePath).normalize()
        val path = projectRoot.resolve(note.location.workspacePath).normalize()
        if (!path.startsWith(projectRoot)) {
            return NavigationOutcome.Warning("Путь заметки выходит за пределы проекта")
        }
        val realPath = runCatching { ReviewNoteTargetBoundary.resolve(projectRoot, path) }.getOrNull()
            ?: return NavigationOutcome.Warning("Цель заметки больше не существует")
        val file = LocalFileSystem.getInstance().findFileByNioFile(realPath)
            ?: return NavigationOutcome.Warning("Файл заметки больше не существует")
        if (file.`is`(VFileProperty.SYMLINK)) {
            return NavigationOutcome.Warning("Символьная ссылка не может быть целью заметки")
        }
        if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
            return NavigationOutcome.Warning("Файл заметки не входит в content roots проекта")
        }
        if (note.location.target == "directory") {
            if (!file.isDirectory || file.`is`(VFileProperty.SYMLINK)) {
                return NavigationOutcome.Warning("Каталог заметки больше не существует или небезопасен")
            }
            return NavigationOutcome.Directory(file)
        }
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return NavigationOutcome.Warning("Файл заметки нельзя открыть как текст")
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
                Messages.showWarningDialog(project, outcome.reason, "Нужна ручная привязка")
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
