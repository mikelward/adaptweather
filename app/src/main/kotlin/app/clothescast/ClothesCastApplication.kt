package app.clothescast

import android.app.Application
import app.clothescast.alarm.DailyAlarmScheduler
import app.clothescast.calendar.CalendarContractEventReader
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.data.tts.ElevenLabsTtsClient
import app.clothescast.core.data.tts.GeminiTtsClient
import app.clothescast.core.data.tts.OpenAITtsClient
import app.clothescast.core.data.weather.OpenMeteoClient
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.repository.CalendarEventReader
import app.clothescast.core.domain.repository.WeatherRepository
import app.clothescast.core.domain.usecase.GenerateDailyInsight
import app.clothescast.data.InsightCache
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import app.clothescast.diag.DiagLog
import app.clothescast.location.LocationResolver
import app.clothescast.notification.InsightNotifier
import app.clothescast.notification.NotificationChannelRegistrar
import app.clothescast.notification.TonightInsightNotifier
import app.clothescast.notification.WeatherAlertNotifier
import app.clothescast.tts.AndroidTtsSpeaker
import app.clothescast.tts.AndroidTtsVoiceEnumerator
import app.clothescast.tts.TtsSpeaker
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
class ClothesCastApplication : Application() {
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore.create(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val insightCache: InsightCache by lazy { InsightCache.create(this) }
    val locationResolver: LocationResolver by lazy { LocationResolver(this) }
    val insightNotifier: InsightNotifier by lazy { InsightNotifier(this) }
    val tonightInsightNotifier: TonightInsightNotifier by lazy { TonightInsightNotifier(this) }
    val weatherAlertNotifier: WeatherAlertNotifier by lazy { WeatherAlertNotifier(this) }
    val dailyAlarmScheduler: DailyAlarmScheduler by lazy { DailyAlarmScheduler(this) }
    /**
     * Build an on-device TTS speaker pinned to [voiceId], or to the auto-pick
     * when [voiceId] is null. Constructed per call (matching the cloud
     * speakers) because the chosen voice is part of the speaker's
     * identity — there's no shared engine state to reuse across calls.
     */
    fun deviceTtsSpeaker(voiceId: String? = null): TtsSpeaker = AndroidTtsSpeaker(this, voiceId)

    /**
     * Voice enumeration is stateless and Android-cheap (one engine init per
     * `listVoices` call), but the wrapper itself is harmless to share — used
     * by the Settings voice picker and the "currently using" line.
     */
    val androidTtsVoiceEnumerator: AndroidTtsVoiceEnumerator by lazy { AndroidTtsVoiceEnumerator(this) }
    val calendarEventReader: CalendarEventReader by lazy { CalendarContractEventReader(this) }
    val geminiTtsClient: GeminiTtsClient by lazy { GeminiTtsClient(httpClient, secureKeyStore) }
    val openAiTtsClient: OpenAITtsClient by lazy {
        OpenAITtsClient(httpClient, secureKeyStore.openAiKeyProvider)
    }
    val elevenLabsTtsClient: ElevenLabsTtsClient by lazy {
        ElevenLabsTtsClient(httpClient, secureKeyStore.elevenLabsKeyProvider)
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

    val generateDailyInsight: GenerateDailyInsight by lazy {
        GenerateDailyInsight(
            weatherRepository = weatherRepository,
            calendarEventReader = calendarEventReader,
        )
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DiagLog.install(this)
        NotificationChannelRegistrar.register(this)
        applicationScope.launch {
            try {
                val prefs = settingsRepository.preferences.first()
                dailyAlarmScheduler.schedule(prefs.schedule, ForecastPeriod.TODAY)
                if (prefs.tonightEnabled) {
                    dailyAlarmScheduler.schedule(prefs.tonightSchedule, ForecastPeriod.TONIGHT)
                } else {
                    dailyAlarmScheduler.cancel(ForecastPeriod.TONIGHT)
                }
            } catch (t: Throwable) {
                DiagLog.e(TAG, "Initial alarm scheduling failed", t)
            }
        }
    }

    companion object {
        private const val TAG = "ClothesCastApplication"
    }
}
