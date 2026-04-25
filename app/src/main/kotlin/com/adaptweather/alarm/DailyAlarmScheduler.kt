package com.adaptweather.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.adaptweather.core.domain.model.Schedule
import java.time.Clock
import java.time.Instant

/**
 * Schedules an exact wake-up at the next occurrence of [Schedule]. Wraps AlarmManager
 * so the receiver code stays small and so we can stub the manager in tests.
 *
 * Design choices:
 * - `setExactAndAllowWhileIdle` is the only primitive that fires at a precise wall-clock
 *   time even in Doze. The plan calls for it.
 * - Permission is gated by `USE_EXACT_ALARM` (API 33+, no user prompt) and
 *   `SCHEDULE_EXACT_ALARM` (API 31-32, granted by default). On API <31 no permission is
 *   needed. If we ever ship on API <33 with the prompt path, callers should fall back to
 *   `setAndAllowWhileIdle` when [AlarmManager.canScheduleExactAlarms] is false.
 * - `Schedule.zoneId` is intentionally re-resolved by the repository on each read so DST
 *   and travel changes pick up automatically. This scheduler does not cache the zone.
 */
class DailyAlarmScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun schedule(schedule: Schedule) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: run {
            Log.w(TAG, "AlarmManager unavailable; alarm not scheduled")
            return
        }

        val triggerAt: Instant = schedule.nextOccurrenceAfter(clock.instant())
        val pendingIntent = pendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // USE_EXACT_ALARM (API 33+) auto-grants this; on the API 31-32 shim path it's
            // also granted by default. If somehow it's denied (user revoked manually),
            // fall back to setAndAllowWhileIdle so the user still gets the notification,
            // just possibly drifted by a few minutes.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
            Log.w(TAG, "Exact-alarm permission denied; using inexact alarm at $triggerAt")
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
        Log.i(TAG, "Daily insight alarm armed for $triggerAt")
    }

    fun cancel() {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    companion object {
        private const val TAG = "DailyAlarmScheduler"
        private const val ALARM_REQUEST_CODE = 0xADA1

        internal fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
            return PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
