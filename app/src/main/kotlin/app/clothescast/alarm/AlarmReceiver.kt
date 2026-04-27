package app.clothescast.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.clothescast.ClothesCastApplication
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.work.FetchAndNotifyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when an insight alarm goes off. Two actions arrive on this receiver:
 *
 *  - [ACTION_FIRE]         — the morning ([ForecastPeriod.TODAY]) alarm
 *  - [ACTION_FIRE_TONIGHT] — the evening ([ForecastPeriod.TONIGHT]) alarm
 *
 * For each, the receiver:
 * 1. Enqueues a WorkManager OneTimeWorkRequest ([FetchAndNotifyWorker]) which actually
 *    does the fetch + insight + notification under network/retry constraints.
 * 2. Re-arms the same slot's alarm for the next day. Re-arming here (rather than only
 *    inside the Worker) keeps the alarm chain alive even if the Worker is permanently
 *    failing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = when (intent.action) {
            ACTION_FIRE -> ForecastPeriod.TODAY
            ACTION_FIRE_TONIGHT -> ForecastPeriod.TONIGHT
            else -> return
        }
        Log.i(TAG, "AlarmReceiver fired (action=${intent.action}, period=$period)")

        // Enqueue the Worker synchronously — it does its own off-thread work.
        FetchAndNotifyWorker.enqueueOneShot(context.applicationContext, period = period)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val app = context.applicationContext as ClothesCastApplication
                val prefs = app.settingsRepository.preferences.first()
                val schedule = when (period) {
                    ForecastPeriod.TODAY -> prefs.schedule
                    ForecastPeriod.TONIGHT -> prefs.tonightSchedule
                }
                DailyAlarmScheduler(context.applicationContext).schedule(schedule, period)
            } catch (t: Throwable) {
                Log.e(TAG, "Re-arm failed for $period", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.clothescast.alarm.FIRE"
        const val ACTION_FIRE_TONIGHT = "app.clothescast.alarm.FIRE_TONIGHT"
        private const val TAG = "AlarmReceiver"
    }
}
