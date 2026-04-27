package app.clothescast.work

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
import app.clothescast.ClothesCastApplication
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.insight.InsightFormatter
import app.clothescast.tts.ElevenLabsTtsSpeaker
import app.clothescast.tts.GeminiTtsSpeaker
import app.clothescast.tts.OpenAITtsSpeaker
import app.clothescast.tts.resolve
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

    private val app: ClothesCastApplication
        get() = applicationContext as ClothesCastApplication

    override suspend fun doWork(): Result {
        val prefs = try {
            app.settingsRepository.preferences.first()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read user preferences; retrying", t)
            return Result.retry()
        }

        val period = inputData.getString(KEY_PERIOD)
            ?.let { runCatching { ForecastPeriod.valueOf(it) }.getOrNull() }
            ?: ForecastPeriod.TODAY

        // The tonight alarm rearms blindly via AlarmReceiver; the user's enable
        // toggle is honoured here so a stale alarm doesn't ship a tonight insight
        // after the user disabled the feature.
        if (period == ForecastPeriod.TONIGHT && !prefs.tonightEnabled) {
            Log.i(TAG, "Tonight insight is disabled; skipping.")
            return Result.success()
        }

        val location = resolveLocation(prefs)
        val today = LocalDate.now()

        // Honour force-refresh only when it's still the day the user tapped on. If
        // the request was enqueued near midnight and only ran after the date rolled
        // over (offline retries, deferred backoff), the *new* day's cache should
        // win — otherwise we'd silently bypass it and burn an extra Gemini call on
        // a stale tap.
        val requestedEpochDay = inputData.getLong(KEY_REQUESTED_EPOCH_DAY, Long.MIN_VALUE)
        val forceRequested = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val forceRefresh = forceRequested && requestedEpochDay == today.toEpochDay()
        if (forceRequested && !forceRefresh) {
            Log.i(TAG, "Ignoring force refresh from a previous day (requested=$requestedEpochDay, today=${today.toEpochDay()}).")
        }

        // 24h cost cap: if we already generated an insight for today, redeliver it
        // rather than refetching. Same path serves the morning alarm and any
        // "Fire insight now" debug taps later in the day.
        //
        // The Today screen's Refresh button sets [KEY_FORCE_REFRESH] = true so an
        // explicit user tap always regenerates — without that flag, tapping Refresh
        // on the same calendar day just redelivers the same cached payload, which
        // is surprising for the user.
        //
        // TODO: opportunistic auto-refresh — when the user opens the app and the
        // cached insight is more than ~1h old, regenerate (bypassing the same-day
        // cache). Avoids a full Refresh tap when the clothes / forecast has moved
        // since the morning generation.
        val cached = if (forceRefresh) {
            Log.i(TAG, "Force refresh requested; bypassing today's cache.")
            null
        } else {
            runCatching { app.insightCache.forPeriodToday(today, period) }.getOrNull()
        }
        if (cached != null) {
            Log.i(TAG, "Using cached $period insight for ${cached.forDate}.")
            return runCatching { deliver(cached, prefs, formatProse(cached, prefs)) }
                .map { Result.success() }
                .getOrElse {
                    Log.e(TAG, "Cached delivery failed; falling through to fresh generate.", it)
                    fresh(location, prefs, period)
                }
        }

        return fresh(location, prefs, period)
    }

    private suspend fun fresh(
        location: Location,
        prefs: UserPreferences,
        period: ForecastPeriod,
    ): Result {
        return try {
            val result = app.generateDailyInsight(location, prefs, period)
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
            // Render once per delivery so notification, TTS, and the audit log
            // all share the same string and we don't reconfigure the
            // Configuration-overridden Resources three times per fire.
            val prose = formatProse(insight, prefs)
            deliver(insight, prefs, prose)
            Log.i(TAG, "Insight delivered for ${insight.forDate}: $prose")
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

    private suspend fun deliver(insight: Insight, prefs: UserPreferences, prose: String) {
        when (insight.period) {
            ForecastPeriod.TODAY -> deliverToday(insight, prefs, prose)
            ForecastPeriod.TONIGHT -> deliverTonight(insight, prefs, prose)
        }
    }

    private suspend fun deliverToday(insight: Insight, prefs: UserPreferences, prose: String) {
        val mode = prefs.deliveryMode
        if (mode == DeliveryMode.NOTIFICATION_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            app.insightNotifier.notify(insight, prose)
        }
        if (mode == DeliveryMode.TTS_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            speakWithFallback(prose, prefs)
        }
    }

    /**
     * Tonight delivery honours the user's [DeliveryMode] (consistent with
     * [deliverToday]) and is also event-gated:
     *  - Notification posts only when the delivery mode includes notifications,
     *    matching today's behaviour. The notifier still picks the silent channel
     *    vs the default-priority channel based on whether there are calendar
     *    events tonight.
     *  - TTS only speaks when the delivery mode includes TTS *and* there are
     *    events tonight. If the evening is empty there's nothing to interrupt
     *    for, even on a TTS-enabled mode.
     */
    private fun formatProse(insight: Insight, prefs: UserPreferences): String =
        InsightFormatter.forRegion(applicationContext, prefs.region).format(insight.summary)

    private suspend fun deliverTonight(insight: Insight, prefs: UserPreferences, prose: String) {
        val mode = prefs.deliveryMode
        if (mode == DeliveryMode.NOTIFICATION_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            app.tonightInsightNotifier.notify(insight, prose)
        }
        if (!insight.hasEvents) {
            Log.i(TAG, "Tonight insight has no events; skipping TTS.")
            return
        }
        if (mode == DeliveryMode.TTS_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            speakWithFallback(prose, prefs)
        }
    }

    /**
     * Speaks via the user-preferred engine; on Gemini / OpenAI failure (network,
     * quota, missing key) falls back to the on-device engine so the user still
     * hears something. We never let a TTS error fail the worker — the notification
     * path is the primary delivery channel and has already fired by this point.
     */
    private suspend fun speakWithFallback(text: String, prefs: UserPreferences) {
        val locale = prefs.voiceLocale.resolve()
        when (prefs.ttsEngine) {
            TtsEngine.GEMINI -> {
                try {
                    GeminiTtsSpeaker(app.geminiTtsClient, voiceName = prefs.geminiVoice).speak(text, locale)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "Gemini TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.OPENAI -> {
                try {
                    OpenAITtsSpeaker(app.openAiTtsClient, voice = prefs.openAiVoice).speak(text, locale)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "OpenAI TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.ELEVENLABS -> {
                try {
                    ElevenLabsTtsSpeaker(app.elevenLabsTtsClient, voiceId = prefs.elevenLabsVoice).speak(text, locale)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "ElevenLabs TTS failed; falling back to device TTS.", t)
                }
            }
            TtsEngine.DEVICE -> Unit
        }
        try {
            app.deviceTtsSpeaker.speak(text, locale)
        } catch (t: Throwable) {
            Log.w(TAG, "Device TTS failed; insight is still posted as notification.", t)
        }
    }

    companion object {
        private const val TAG = "FetchAndNotifyWorker"
        const val UNIQUE_WORK_NAME = "daily_insight_fetch"
        const val UNIQUE_WORK_NAME_TONIGHT = "tonight_insight_fetch"

        // Output Data keys for surfacing failure reasons in the UI.
        const val KEY_REASON = "reason"
        const val KEY_REASON_DETAIL = "reason_detail"

        const val REASON_UNEXPECTED_HTTP = "unexpected_http"
        const val REASON_UNHANDLED = "unhandled"

        /** Set true via [enqueueOneShot] when the user explicitly taps Refresh. */
        private const val KEY_FORCE_REFRESH = "force_refresh"

        /**
         * Epoch-day of the calendar date the force-refresh was requested for. Used
         * to drop a stale force flag if the worker only runs after midnight (e.g. a
         * tap at 23:59 that retried offline until 00:05).
         */
        private const val KEY_REQUESTED_EPOCH_DAY = "requested_epoch_day"

        /** Which slice of the day this run is for; defaults to TODAY when absent. */
        private const val KEY_PERIOD = "period"

        // Fallback when the user hasn't set a location yet — the pipeline still runs and
        // posts a (London) insight rather than silently failing. The Settings screen
        // surfaces an empty-location card so the user can change it.
        private val DEFAULT_LOCATION = Location(
            latitude = 51.5074,
            longitude = -0.1278,
            displayName = "London (default)",
        )

        fun enqueueOneShot(
            context: Context,
            force: Boolean = false,
            period: ForecastPeriod = ForecastPeriod.TODAY,
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FetchAndNotifyWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_FORCE_REFRESH to force,
                        KEY_REQUESTED_EPOCH_DAY to LocalDate.now().toEpochDay(),
                        KEY_PERIOD to period.name,
                    )
                )
                .build()

            // Alarm-driven enqueues use KEEP so a still-retrying run isn't duplicated.
            // User-initiated force enqueues use REPLACE — the user just tapped Refresh
            // expecting a fresh fetch, so cancel any in-flight retry and start over.
            val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            val workName = when (period) {
                ForecastPeriod.TODAY -> UNIQUE_WORK_NAME
                ForecastPeriod.TONIGHT -> UNIQUE_WORK_NAME_TONIGHT
            }
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, policy, request)
        }
    }
}
