package ai.agentreviewnotes.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewNoteDateFilterPresetTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 22)

    @Test
    fun `all dates has open bounds`() {
        val bounds = ReviewNoteDateFilterPreset.ALL_DATES.bounds(today, zone)

        assertNull(bounds.from)
        assertNull(bounds.to)
    }

    @Test
    fun `today covers the complete local day`() {
        val bounds = ReviewNoteDateFilterPreset.TODAY.bounds(today, zone)

        assertEquals(Instant.parse("2026-08-22T00:00:00Z"), bounds.from)
        assertEquals(Instant.parse("2026-08-22T23:59:59.999999999Z"), bounds.to)
    }

    @Test
    fun `last seven days includes today and six preceding days`() {
        val bounds = ReviewNoteDateFilterPreset.LAST_7_DAYS.bounds(today, zone)

        assertEquals(Instant.parse("2026-08-16T00:00:00Z"), bounds.from)
        assertEquals(Instant.parse("2026-08-22T23:59:59.999999999Z"), bounds.to)
    }
}
