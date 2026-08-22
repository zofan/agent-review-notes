package ai.agentreviewnotes.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ReviewNoteDateBounds(val from: Instant?, val to: Instant?)

internal enum class ReviewNoteDateFilterPreset(
    private val title: String,
    private val daysIncludingToday: Long?,
) {
    ALL_DATES("Any date", null),
    TODAY("Today", 1),
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30),
    ;

    fun bounds(today: LocalDate, zone: ZoneId): ReviewNoteDateBounds {
        val days = daysIncludingToday ?: return ReviewNoteDateBounds(null, null)
        val fromDate = today.minusDays(days - 1)
        return ReviewNoteDateBounds(
            from = fromDate.atStartOfDay(zone).toInstant(),
            to = today.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1),
        )
    }

    override fun toString(): String = title
}
