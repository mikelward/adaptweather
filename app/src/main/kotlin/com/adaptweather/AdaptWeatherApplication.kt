package com.adaptweather

import android.app.Application
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository

/**
 * Lightweight DI: lazy singletons for repositories that depend on Android Context.
 * Will move to Hilt once we have more than a handful of consumers.
 */
class AdaptWeatherApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
}
