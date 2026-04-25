package com.adaptweather

import android.app.Application
import android.util.Log
import com.adaptweather.alarm.DailyAlarmScheduler
import com.adaptweather.core.data.insight.DirectGeminiClient
import com.adaptweather.core.data.weather.OpenMeteoClient
import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.repository.WeatherRepository
import com.adaptweather.core.domain.usecase.GenerateDailyInsight
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import com.adaptweather.notification.InsightNotifier
import com.adaptweather.notification.NotificationChannelRegistrar
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Lightweight DI: lazy singletons for things that depend on Android Context or that
 * the Worker / UI / receivers all share. Will move to Hilt once we have more than a
 * handful of consumers.
 */
class AdaptWeatherApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val insightNotifier: InsightNotifier by lazy { InsightNotifier(this) }
    val dailyAlarmScheduler: DailyAlarmScheduler by lazy { DailyAlarmScheduler(this) }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    val weatherRepository: WeatherRepository by lazy { OpenMeteoClient(httpClient) }
    val insightGenerator: InsightGenerator by lazy { DirectGeminiClient(httpClient, secureKeyStore) }
    val generateDailyInsight: GenerateDailyInsight by lazy {
        GenerateDailyInsight(weatherRepository, insightGenerator)
    }

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
