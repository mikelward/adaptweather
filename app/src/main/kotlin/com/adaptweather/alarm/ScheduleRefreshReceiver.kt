package com.adaptweather.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adaptweather.AdaptWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the daily-insight alarm whenever the wall-clock context changes:
 * - device boot (alarms are wiped)
 * - app update (Android also wipes alarms)
 * - timezone change (the user travelled across zones)
 * - locale change (the next insight should be generated in the new language)
 *
 * The schedule's `zoneId` is re-resolved from `ZoneId.systemDefault()` at read time, so
 * we just need to recompute "next 7am" with the now-current zone and rearm.
 */
class ScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "ScheduleRefreshReceiver action=${intent.action}")

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val app = context.applicationContext as AdaptWeatherApplication
                val schedule = app.settingsRepository.preferences.first().schedule
                DailyAlarmScheduler(context.applicationContext).schedule(schedule)
            } catch (t: Throwable) {
                Log.e(TAG, "Re-arm failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleRefreshReceiver"
    }
}
