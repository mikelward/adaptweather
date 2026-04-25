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
 * Fires when the daily-insight alarm goes off. v1 placeholder behaviour: log the event
 * and immediately re-arm the alarm for the next day. The follow-up PR will enqueue a
 * WorkManager OneTimeWorkRequest that fetches forecast → calls Gemini → posts the
 * notification, then re-arms.
 *
 * Re-arming inside the receiver (rather than only from the worker) keeps the alarm
 * chain alive even if the worker fails before it can schedule the next one.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        Log.i(TAG, "AlarmReceiver fired (action=${intent.action})")

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
        const val ACTION_FIRE = "com.adaptweather.alarm.FIRE"
        private const val TAG = "AlarmReceiver"
    }
}
