package ai.agentreviewnotes.marker

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewNoteMarkerOffsetTest {
    @Test
    fun `позиция внутри документа сохраняется`() {
        assertEquals(5, ReviewNoteMarkerOffset.forDocument(5, 10))
    }

    @Test
    fun `позиция в конце документа привязывается к последнему символу`() {
        assertEquals(9, ReviewNoteMarkerOffset.forDocument(10, 10))
    }

    @Test
    fun `пустой документ и некорректные позиции не создают marker`() {
        assertNull(ReviewNoteMarkerOffset.forDocument(0, 0))
        assertNull(ReviewNoteMarkerOffset.forDocument(-1, 10))
        assertNull(ReviewNoteMarkerOffset.forDocument(11, 10))
    }

    @Test
    fun `позиция на границе относится только к следующему диапазону`() {
        assertEquals(false, ReviewNoteMarkerOffset.containsCharacter(0, 5, 5))
        assertEquals(true, ReviewNoteMarkerOffset.containsCharacter(5, 10, 5))
    }

    @Test
    fun `line marker provider нормализует позицию заметки`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/ai/agentreviewnotes/marker/ReviewNoteLineMarkerProvider.kt"),
        )

        assertContains(source, "ReviewNoteMarkerOffset.forDocument")
        assertContains(source, "ReviewNoteMarkerOffset.containsCharacter")
    }
}
