package com.adaptweather

import android.app.Application
import android.util.Log
import com.adaptweather.alarm.DailyAlarmScheduler
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import com.adaptweather.notification.InsightNotifier
import com.adaptweather.notification.NotificationChannelRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight DI: lazy singletons for repositories that depend on Android Context.
 * Will move to Hilt once we have more than a handful of consumers.
 */
class AdaptWeatherApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val insightNotifier: InsightNotifier by lazy { InsightNotifier(this) }
    val dailyAlarmScheduler: DailyAlarmScheduler by lazy { DailyAlarmScheduler(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannelRegistrar.register(this)
        applicationScope.launch {
            try {
                val schedule = settingsRepository.preferences.first().schedule
                dailyAlarmScheduler.schedule(schedule)
            } catch (t: Throwable) {
                Log.e(TAG, "Initial alarm scheduling failed", t)
            }
        }
    }

    companion object {
        private const val TAG = "AdaptWeatherApplication"
    }
}
