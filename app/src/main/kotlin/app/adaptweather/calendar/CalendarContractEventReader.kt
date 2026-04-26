package app.adaptweather.calendar

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import app.adaptweather.core.domain.model.CalendarEvent
import app.adaptweather.core.domain.repository.CalendarEventReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads device calendar events for a single local day via [CalendarContract.Instances].
 *
 * Why `Instances` and not `Events`: `Events` rows describe recurring patterns; one
 * recurring meeting that fires every weekday is a single Events row but we want each
 * individual occurrence. The `Instances` view materialises occurrences into the
 * `[begin, end]` window we query, so a "9am standup, every weekday" only shows up
 * once per day with the correct start time.
 *
 * Returns an empty list on any failure path (missing permission, missing provider,
 * cursor crash) so the daily insight pipeline degrades gracefully to no events.
 */
class CalendarContractEventReader(private val context: Context) : CalendarEventReader {

    override suspend fun eventsForDay(date: LocalDate, zoneId: ZoneId): List<CalendarEvent> {
        if (!CalendarPermission.isGranted(context)) {
            Log.i(TAG, "READ_CALENDAR not granted; skipping calendar read.")
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            runCatching { query(date, zoneId) }
                .onFailure { Log.w(TAG, "Calendar query failed; degrading to no events.", it) }
                .getOrDefault(emptyList())
        }
    }

    @SuppressLint("MissingPermission")
    private fun query(date: LocalDate, zoneId: ZoneId): List<CalendarEvent> {
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startOfDay)
        ContentUris.appendId(uriBuilder, endOfDay)
        val uri: Uri = uriBuilder.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.AVAILABILITY,
        )
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val results = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val locationIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val statusIdx = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
            val availabilityIdx = cursor.getColumnIndex(CalendarContract.Instances.AVAILABILITY)

            while (cursor.moveToNext()) {
                // Cancelled events are still in the cursor; the calendar tie-in would
                // pair an item against a meeting that isn't happening, so drop early.
                // FREE busy slots are typically calendar holds the user doesn't want
                // surfaced as actual events either.
                if (statusIdx >= 0 && cursor.getInt(statusIdx) == CalendarContract.Instances.STATUS_CANCELED) continue
                if (availabilityIdx >= 0 && cursor.getInt(availabilityIdx) == CalendarContract.Instances.AVAILABILITY_FREE) continue

                val title = cursor.takeIf { titleIdx >= 0 }?.getString(titleIdx)?.takeIf { it.isNotBlank() }
                    ?: continue
                val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else continue
                val end = if (endIdx >= 0) cursor.getLong(endIdx) else continue
                val location = locationIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }?.takeIf { it.isNotBlank() }
                val allDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) != 0

                // CalendarContract stores all-day events in UTC midnight-to-midnight; converting
                // those into the user's zone shifts them off the day boundary. Keep them as
                // pure all-day markers and project the rest into wall-clock in the user zone.
                val (start, finish) = if (allDay) {
                    LocalTime.MIDNIGHT to LocalTime.MIDNIGHT
                } else {
                    val s = Instant.ofEpochMilli(begin).atZone(zoneId).toLocalTime()
                    val e = Instant.ofEpochMilli(end).atZone(zoneId).toLocalTime()
                    s to e
                }

                results += CalendarEvent(
                    title = title,
                    start = start,
                    end = finish,
                    location = location,
                    allDay = allDay,
                )
            }
        }
        return results
    }

    private companion object {
        private const val TAG = "CalendarReader"
    }
}
