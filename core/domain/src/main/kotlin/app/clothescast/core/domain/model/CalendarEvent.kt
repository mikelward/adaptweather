package app.clothescast.core.domain.model

import java.time.LocalTime

/**
 * One scheduled event read from the user's device calendar, projected into the
 * local day the daily insight is being generated for. Times are wall-clock in the
 * user's zone — the reader is responsible for applying timezone conversion before
 * constructing the model so downstream code never has to think about Instants.
 *
 * Privacy posture: only the [title], [start]/[end], and an optional [location] line
 * are carried. Descriptions, attendees, organizers, and account info are not
 * surfaced. The current consumer [app.clothescast.core.domain.usecase.RenderInsightSummary]
 * never includes the location in the rendered string, but the field is kept so a
 * future "what to bring + where" sentence can use it without a model migration.
 */
data class CalendarEvent(
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val location: String? = null,
    val allDay: Boolean = false,
) {
    /**
     * True when [time] falls within `[start, end)`. All-day events match every
     * non-midnight time so the precip-peak overlap check below them never
     * accidentally pairs an umbrella with "your all-day Public holiday".
     */
    fun overlaps(time: LocalTime): Boolean {
        if (allDay) return false
        if (start == end) return time == start
        return !time.isBefore(start) && time.isBefore(end)
    }
}
