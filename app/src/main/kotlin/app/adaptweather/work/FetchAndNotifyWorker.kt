package app.adaptweather.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.adaptweather.AdaptWeatherApplication
import app.adaptweather.core.domain.model.DeliveryMode
import app.adaptweather.core.domain.model.Insight
import app.adaptweather.core.domain.model.Location
import app.adaptweather.core.domain.model.TtsEngine
import app.adaptweather.core.domain.model.UserPreferences
import app.adaptweather.tts.ElevenLabsTtsSpeaker
import app.adaptweather.tts.GeminiTtsSpeaker
import app.adaptweather.tts.OpenAITtsSpeaker
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Performs one daily fetch + insight + notify cycle. Runs in a WorkManager
 * OneTimeWorkRequest enqueued by AlarmReceiver, with NetworkType.CONNECTED and
 * exponential backoff so transient failures (no Wi-Fi at 7am) retry on next connectivity.
 *
 * Outcomes:
 * - Result.success — insight posted (or notification permission denied; we still cached the insight).
 * - Result.retry — transient network / 5xx from OpenMeteo; WorkManager retries with backoff.
 * - Result.failure — non-recoverable HTTP error or unhandled throwable.
 */
class FetchAndNotifyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val app: AdaptWeatherApplication
        get() = applicationContext as AdaptWeatherApplication

    override suspend fun doWork(): Result {
        val prefs = try {
            app.settingsRepository.preferences.first()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read user preferences; retrying", t)
            return Result.retry()
        }

        val location = resolveLocation(prefs)
        val today = LocalDate.now()

        // 24h cost cap: if we already generated an insight for today, redeliver it
        // rather than refetching. Same path serves the morning alarm and any
        // "Fire insight now" debug taps later in the day.
        val cached = runCatching { app.insightCache.forToday(today) }.getOrNull()
        if (cached != null) {
            Log.i(TAG, "Using cached insight for ${cached.forDate}.")
            return runCatching { deliver(cached, prefs) }
                .map { Result.success() }
                .getOrElse {
                    Log.e(TAG, "Cached delivery failed; falling through to fresh generate.", it)
                    fresh(location, prefs)
                }
        }

        return fresh(location, prefs)
    }

    private suspend fun fresh(
        location: Location,
        prefs: UserPreferences,
    ): Result {
        return try {
            val result = app.generateDailyInsight(location, prefs)
            val insight = result.insight
            // Severe alerts are out-of-band: post them as separate high-priority
            // notifications on every fresh fetch, regardless of whether the daily
            // summary itself is blank or suppressed.
            result.alerts.filter { it.isHighPriority() }.forEach { alert ->
                runCatching { app.weatherAlertNotifier.notify(alert) }
                    .onFailure { Log.w(TAG, "Severe alert notification failed for ${alert.event}.", it) }
            }
            runCatching { app.insightCache.store(insight) }
                .onFailure { Log.w(TAG, "Insight cache write failed; not blocking delivery.", it) }
            deliver(insight, prefs)
            Log.i(TAG, "Insight delivered for ${insight.forDate}: ${insight.summary}")
            Result.success()
        } catch (e: ResponseException) {
            // OpenMeteo 4xx → fail; 5xx → retry with backoff.
            val status = e.response.status
            if (status.value in 500..599) {
                Log.w(TAG, "Server error $status from OpenMeteo; retrying.")
                Result.retry()
            } else {
                Log.e(TAG, "Unexpected HTTP status $status from OpenMeteo", e)
                Result.failure(reason(REASON_UNEXPECTED_HTTP, "$status"))
            }
        } catch (e: ConnectTimeoutException) {
            Log.w(TAG, "Connect timeout; retrying.", e); Result.retry()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Socket timeout; retrying.", e); Result.retry()
        } catch (e: HttpRequestTimeoutException) {
            Log.w(TAG, "Request timeout; retrying.", e); Result.retry()
        } catch (e: IOException) {
            Log.w(TAG, "Network IO failure; retrying.", e); Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled error; failing.", t)
            Result.failure(reason(REASON_UNHANDLED, t.javaClass.simpleName + ": " + (t.message ?: "")))
        }
    }

    private fun reason(code: String, detail: String? = null) =
        if (detail.isNullOrBlank()) workDataOf(KEY_REASON to code)
        else workDataOf(KEY_REASON to code, KEY_REASON_DETAIL to detail)

    private suspend fun resolveLocation(prefs: UserPreferences): Location {
        if (prefs.useDeviceLocation) {
            val device = runCatching { app.locationResolver.resolve() }.getOrNull()
            if (device != null) {
                Log.i(TAG, "Using device-resolved location at ${device.latitude}, ${device.longitude}.")
                return device
            }
            Log.i(TAG, "Device location unavailable; falling back to settings location.")
        }
        return prefs.location ?: DEFAULT_LOCATION
    }

    private suspend fun deliver(insight: Insight, prefs: UserPreferences) {
        // Defensive: a cache from an older app version could have a blank summary
        // (when the LLM rule set occasionally emitted nothing). Don't post a blank
        // notification or speak silence. The current renderer always emits a band
        // sentence, so this guard never trips for fresh insights.
        if (insight.summary.isBlank()) {
            Log.i(TAG, "Insight summary is blank; skipping notification + TTS.")
            return
        }
        val mode = prefs.deliveryMode
        if (mode == DeliveryMode.NOTIFICATION_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            app.insightNotifier.notify(insight)
        }
        if (mode == DeliveryMode.TTS_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            speakWithFallback(insight.spokenText(), prefs)
        }
    }

    /**
     * Speaks via the user-preferred engine; on Gemini / OpenAI failure (network,
     * quota, missing key) falls back to the on-device engine so the user still
     * hears something. We never let a TTS error fail the worker — the notification
     * path is the primary delivery channel and has already fired by this point.
     */
    private suspend fun speakWithFallback(text: String, prefs: UserPreferences) {
        when (prefs.ttsEngine) {
            TtsEngine.GEMINI -> {
                try {
                    GeminiTtsSpeaker(app.geminiTtsClient, voiceName = prefs.geminiVoice).speak(text)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "Gemini TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.OPENAI -> {
                try {
                    OpenAITtsSpeaker(app.openAiTtsClient, voice = prefs.openAiVoice).speak(text)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "OpenAI TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.ELEVENLABS -> {
                try {
                    ElevenLabsTtsSpeaker(app.elevenLabsTtsClient, voiceId = prefs.elevenLabsVoice).speak(text)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "ElevenLabs TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.DEVICE -> Unit
        }
        try {
            app.deviceTtsSpeaker.speak(text)
        } catch (t: Throwable) {
            Log.w(TAG, "Device TTS failed; insight is still posted as notification.", t)
        }
    }

    companion object {
        private const val TAG = "FetchAndNotifyWorker"
        const val UNIQUE_WORK_NAME = "daily_insight_fetch"

        // Output Data keys for surfacing failure reasons in the UI.
        const val KEY_REASON = "reason"
        const val KEY_REASON_DETAIL = "reason_detail"

        const val REASON_UNEXPECTED_HTTP = "unexpected_http"
        const val REASON_UNHANDLED = "unhandled"

        // Fallback when the user hasn't set a location yet — the pipeline still runs and
        // posts a (London) insight rather than silently failing. The Settings screen
        // surfaces an empty-location card so the user can change it.
        private val DEFAULT_LOCATION = Location(
            latitude = 51.5074,
            longitude = -0.1278,
            displayName = "London (default)",
        )

        fun enqueueOneShot(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FetchAndNotifyWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // KEEP: if a previous run is still in flight (e.g. retrying), don't start
            // a duplicate. The next 7am alarm will enqueue afresh.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
