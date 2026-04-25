package com.adaptweather

import android.app.Application
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import com.adaptweather.notification.InsightNotifier
import com.adaptweather.notification.NotificationChannelRegistrar

/**
 * Lightweight DI: lazy singletons for repositories that depend on Android Context.
 * Will move to Hilt once we have more than a handful of consumers.
 */
class AdaptWeatherApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val insightNotifier: InsightNotifier by lazy { InsightNotifier(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannelRegistrar.register(this)
    }
}
