package ai.agentreviewnotes.marker

import ai.agentreviewnotes.anchor.AnchorResult
import ai.agentreviewnotes.anchor.ReviewNoteAnchor
import ai.agentreviewnotes.model.ReviewNote
import ai.agentreviewnotes.presentation.ReviewNotePresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class ReviewNoteEditorHighlighter(private val project: Project) {
    private val log = logger<ReviewNoteEditorHighlighter>()
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private val generations = ReviewNoteRefreshGeneration()
    private val pending = ConcurrentHashMap<String, ScheduledFuture<*>>()

    init {
        Disposer.register(project) {
            pending.values.forEach { it.cancel(false) }
            pending.clear()
        }
    }

    fun refreshAll() {
        val epoch = generations.invalidateAll()
        runOnEdt {
            FileEditorManager.getInstance(project).openFiles.forEach { requestFileOnEdt(it, 0, epoch) }
        }
    }

    fun refreshFile(file: VirtualFile) {
        val epoch = generations.currentEpoch()
        runOnEdt { requestFileOnEdt(file, 0, epoch) }
    }

    fun refreshAfterDocumentChange(file: VirtualFile) {
        val epoch = generations.currentEpoch()
        runOnEdt { requestFileOnEdt(file, DOCUMENT_CHANGE_DELAY_MS, epoch) }
    }

    private fun requestFileOnEdt(file: VirtualFile, delayMillis: Long, capturedEpoch: Long) {
        if (project.isDisposed) return
        if (!generations.isEpochCurrent(capturedEpoch)) return
        val editors = FileEditorManager.getInstance(project).getAllEditors(file)
            .filterIsInstance<TextEditor>()
            .map { it.editor }
            .filterNot(Editor::isDisposed)
        val document = editors.firstOrNull()?.document ?: return
        val key = file.canonicalPath ?: file.path
        val generation = generations.next(key)
        val capturedStamp = document.modificationStamp
        val task = scheduler.schedule(
            {
                try {
                    prepareOffEdt(file, key, generation, capturedEpoch, capturedStamp, document, editors)
                } catch (error: ProcessCanceledException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.warn("Failed to prepare review note editor decorations for ${file.path}", error)
                }
            },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
        pending.put(key, task)?.cancel(false)
    }

    private fun prepareOffEdt(
        file: VirtualFile,
        key: String,
        generation: Long,
        capturedEpoch: Long,
        capturedStamp: Long,
        document: Document,
        editors: List<Editor>,
    ) {
        if (project.isDisposed || !generations.isCurrent(key, generation, capturedEpoch)) return
        val input = ApplicationManager.getApplication().runReadAction<PreparationInput?> {
            if (project.isDisposed || document.modificationStamp != capturedStamp) return@runReadAction null
            PreparationInput(
                text = document.immutableCharSequence.toString(),
                notes = ReviewNoteEditorNotes.forFile(project, file),
            )
        } ?: return
        val sha256 = ReviewNoteAnchor.sha256(input.text)
        val decorations = input.notes.mapNotNull { note ->
            val resolved = ReviewNoteAnchor.resolve(note, input.text, sha256) as? AnchorResult.Resolved
                ?: return@mapNotNull null
            val range = ReviewNoteHighlightRange.resolve(
                resolved.offset,
                note.anchor.selection.length,
                input.text.length,
            ) ?: return@mapNotNull null
            val presentation = ReviewNotePresentations.forWireValue(note.kind)
            ReviewNoteEditorDecoration(
                range = range,
                textAttributes = ReviewNoteEditorMarkup.textAttributes(presentation),
                targetPointAtEnd = ReviewNoteEditorLogicalTarget.pointAtEnd(
                    anchorOffset = resolved.offset,
                    selectionLength = note.anchor.selection.length,
                    visualRange = range,
                ),
                noteId = note.id,
                priority = presentation.priority,
                blockInlayRenderer = ReviewNoteBlockInlayRenderer(project, note, presentation),
            )
        }

        runOnEdt {
            if (!generations.isCurrent(key, generation, capturedEpoch) || document.modificationStamp != capturedStamp) return@runOnEdt
            val appliedInlays = editors.filterNot(Editor::isDisposed).sumOf { editor ->
                ReviewNoteEditorMarkup.replace(editor, decorations)
            }
            if (decorations.isNotEmpty()) {
                log.info(
                    "Applied ${decorations.size} review note editor decoration(s) and $appliedInlays note block inlay(s) to ${file.path}",
                )
            }
        }
    }

    private fun runOnEdt(action: () -> Unit) {
        val safeAction: () -> Unit = safe@{
            if (project.isDisposed) return@safe
            action()
        }
        if (ApplicationManager.getApplication().isDispatchThread) {
            safeAction()
        } else {
            ApplicationManager.getApplication().invokeLater(safeAction)
        }
    }

    private data class PreparationInput(
        val text: String,
        val notes: List<ReviewNote>,
    )

    private companion object {
        const val DOCUMENT_CHANGE_DELAY_MS = 200L
    }
}
