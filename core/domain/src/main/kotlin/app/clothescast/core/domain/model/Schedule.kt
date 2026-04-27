package app.clothescast.core.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the daily insight should fire. Stored as wall-clock + zone so DST and
 * timezone changes are resolved at scheduling time, not at the time the schedule was set.
 */
data class Schedule(
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val zoneId: ZoneId,
) {
    init {
        require(days.isNotEmpty()) { "Schedule must include at least one day" }
    }

    /**
     * Earliest active fire time strictly after [now], resolved in [zoneId].
     */
    fun nextOccurrenceAfter(now: Instant): Instant {
        val zoned = now.atZone(zoneId)
        for (offset in 0..7) {
            val candidate = zoned.plusDays(offset.toLong()).with(time)
            if (candidate.dayOfWeek in days && candidate.toInstant().isAfter(now)) {
                return candidate.toInstant()
            }
        }
        error("Unreachable: at least one day is selected so a match exists within 7 days")
    }

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        /** Morning insight default — 07:00 every day. */
        fun default(zoneId: ZoneId = ZoneId.systemDefault()): Schedule =
            Schedule(time = LocalTime.of(7, 0), days = EVERY_DAY, zoneId = zoneId)

        /**
         * Tonight insight default — 19:00 every day. The tonight pass mirrors the
         * morning one; only the wall-clock time and the summary period differ.
         */
        fun defaultTonight(zoneId: ZoneId = ZoneId.systemDefault()): Schedule =
            Schedule(time = LocalTime.of(19, 0), days = EVERY_DAY, zoneId = zoneId)
    }
}
