package com.adaptweather

import android.app.Application
import android.util.Log
import com.adaptweather.alarm.DailyAlarmScheduler
import com.adaptweather.core.data.insight.DirectGeminiClient
import com.adaptweather.core.data.location.OpenMeteoGeocodingClient
import com.adaptweather.core.data.tts.GeminiTtsClient
import com.adaptweather.core.data.tts.OpenAITtsClient
import com.adaptweather.core.data.weather.OpenMeteoClient
import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.repository.WeatherRepository
import com.adaptweather.core.domain.usecase.GenerateDailyInsight
import com.adaptweather.data.InsightCache
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import com.adaptweather.location.LocationResolver
import com.adaptweather.notification.InsightNotifier
import com.adaptweather.notification.NotificationChannelRegistrar
import com.adaptweather.tts.AndroidTtsSpeaker
import com.adaptweather.tts.TtsSpeaker
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
 *
 * The TTS *clients* are exposed (Gemini, OpenAI) but not the speakers — speakers wrap
 * a per-call voice choice, so they're constructed at the call site from current
 * preferences. The clients themselves are heavy (share the OkHttp engine) so they
 * stay singletons.
 */
class AdaptWeatherApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val insightCache: InsightCache by lazy { InsightCache.create(this) }
    val locationResolver: LocationResolver by lazy { LocationResolver(this) }
    val insightNotifier: InsightNotifier by lazy { InsightNotifier(this) }
    val dailyAlarmScheduler: DailyAlarmScheduler by lazy { DailyAlarmScheduler(this) }
    val deviceTtsSpeaker: TtsSpeaker by lazy { AndroidTtsSpeaker(this) }
    val geminiTtsClient: GeminiTtsClient by lazy { GeminiTtsClient(httpClient, secureKeyStore) }
    val openAiTtsClient: OpenAITtsClient by lazy {
        OpenAITtsClient(httpClient, secureKeyStore.openAiKeyProvider)
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    val weatherRepository: WeatherRepository by lazy { OpenMeteoClient(httpClient) }
    val geocodingClient: OpenMeteoGeocodingClient by lazy { OpenMeteoGeocodingClient(httpClient) }

    /**
     * Build a [GenerateDailyInsight] use case bound to the user-chosen Gemini
     * model. The model id is part of the URL path on each call so we pass it in
     * here rather than caching a singleton — same shape as how TTS speakers are
     * constructed per-call from the user-chosen voice.
     *
     * The httpClient + secure key store are shared across calls; only the thin
     * [DirectGeminiClient] wrapper is reconstructed.
     */
    fun createGenerateDailyInsight(model: String): GenerateDailyInsight {
        val generator: InsightGenerator = DirectGeminiClient(httpClient, secureKeyStore, model = model)
        return GenerateDailyInsight(weatherRepository, generator)
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
