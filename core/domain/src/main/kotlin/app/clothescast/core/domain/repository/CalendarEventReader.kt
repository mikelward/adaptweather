package app.clothescast.core.domain.repository

import app.clothescast.core.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the user's calendar events for a given local day. Implementations are
 * expected to be best-effort: missing permission, an unavailable provider, or a
 * query failure should return an empty list rather than throw, so the daily
 * insight pipeline degrades gracefully to its calendar-free behaviour.
 */
interface CalendarEventReader {
    suspend fun eventsForDay(date: LocalDate, zoneId: ZoneId): List<CalendarEvent>
}
