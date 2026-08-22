package ai.agentreviewnotes.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewNoteCreatedAtFilterTest {
    @Test
    fun `границы периода включительны`() {
        val from = Instant.parse("2026-08-20T00:00:00Z")
        val to = Instant.parse("2026-08-22T23:59:59Z")

        assertTrue(ReviewNoteCreatedAtFilter.isVisible("2026-08-20T00:00:00Z", from, to))
        assertTrue(ReviewNoteCreatedAtFilter.isVisible("2026-08-22T23:59:59Z", from, to))
        assertFalse(ReviewNoteCreatedAtFilter.isVisible("2026-08-19T23:59:59Z", from, to))
        assertFalse(ReviewNoteCreatedAtFilter.isVisible("2026-08-23T00:00:00Z", from, to))
    }

    @Test
    fun `открытые границы и некорректная дата обрабатываются fail closed`() {
        assertTrue(ReviewNoteCreatedAtFilter.isVisible("2026-08-22T10:00:00Z", null, null))
        assertFalse(ReviewNoteCreatedAtFilter.isVisible("not-a-date", null, null))
    }
}
