package app.adaptweather.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.adaptweather.AdaptWeatherApplication
import app.adaptweather.work.FetchAndNotifyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when the daily-insight alarm goes off. The receiver does two things:
 *
 * 1. Enqueues a WorkManager OneTimeWorkRequest ([FetchAndNotifyWorker]) which actually
 *    does the fetch + Gemini call + notification under network/retry constraints.
 * 2. Re-arms the alarm for the next day. Re-arming here (rather than only inside the
 *    Worker) keeps the alarm chain alive even if the Worker is permanently failing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        Log.i(TAG, "AlarmReceiver fired (action=${intent.action})")

        // Enqueue the Worker synchronously — it does its own off-thread work.
        FetchAndNotifyWorker.enqueueOneShot(context.applicationContext)

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
        const val ACTION_FIRE = "app.adaptweather.alarm.FIRE"
        private const val TAG = "AlarmReceiver"
    }
}
