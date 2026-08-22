package ai.agentreviewnotes.ui

import ai.agentreviewnotes.model.NoteAnchor
import ai.agentreviewnotes.model.NoteLocation
import ai.agentreviewnotes.model.NoteResolution
import ai.agentreviewnotes.model.ReviewNote
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewNoteDetailsTest {
    @Test
    fun `подробности содержат цель строки git даты тип статус и сниппет`() {
        val note = ReviewNote(
            id = "note-1",
            status = "resolved",
            kind = "bug",
            message = "Fix it",
            location = NoteLocation(
                workspacePath = "src/main.go",
                vcsRoot = ".",
                vcsPath = "src/main.go",
                head = "abc123",
                fileSha256 = "0".repeat(64),
                startOffset = 10,
                endOffset = 20,
                startLine = 4,
                endLine = 6,
                branch = "main",
            ),
            anchor = NoteAnchor("selected", "before", "after", "run"),
            createdAt = "2026-08-20T10:00:00Z",
            resolution = NoteResolution("done", "2026-08-22T11:00:00Z", null),
        )

        val rows = ReviewNoteDetails.rows(note).associate { it.label to it.value }

        assertEquals("Файл: src/main.go", rows["Цель"])
        assertEquals("4–6", rows["Строки"])
        assertEquals("selected", rows["Сниппет"])
        assertEquals(". (src/main.go)", rows["Репозиторий"])
        assertEquals("main", rows["Ветка"])
        assertEquals("abc123", rows["Git snapshot"])
        assertEquals("2026-08-20T10:00:00Z", rows["Создано"])
        assertEquals("2026-08-22T11:00:00Z", rows["Решено"])
        assertEquals("bug", rows["Тип"])
        assertEquals("resolved", rows["Статус"])
    }

    @Test
    fun `каталог и отсутствующие git resolution поля показаны явно`() {
        val note = ReviewNote(
            id = "note-2",
            status = "open",
            kind = "question",
            message = "Why?",
            location = NoteLocation("docs", null, null, null, "0".repeat(64), 0, 0, 0, 0, target = "directory"),
            anchor = NoteAnchor("", "context", "", null),
            createdAt = "2026-08-22T10:00:00Z",
        )

        val rows = ReviewNoteDetails.rows(note).associate { it.label to it.value }

        assertEquals("Каталог: docs", rows["Цель"])
        assertEquals("—", rows["Строки"])
        assertEquals("Вне Git", rows["Репозиторий"])
        assertEquals("—", rows["Решено"])
        assertEquals("context", rows["Сниппет"])
    }
}
