package app.clothescast.work

import android.content.Context
import app.clothescast.diag.DiagLog
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
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
import app.clothescast.tts.withSpeechAudioFocus
import app.clothescast.widget.OutfitWidget
import io.ktor.client.call.NoTransformationFoundException
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
            DiagLog.e(TAG, "Failed to read user preferences; retrying", t)
            return Result.retry()
        }

        val period = inputData.getString(KEY_PERIOD)
            ?.let { runCatching { ForecastPeriod.valueOf(it) }.getOrNull() }
            ?: ForecastPeriod.TODAY

        // The tonight alarm rearms blindly via AlarmReceiver; the user's enable
        // toggle is honoured here so a stale alarm doesn't ship a tonight insight
        // after the user disabled the feature.
        if (period == ForecastPeriod.TONIGHT && !prefs.tonightEnabled) {
            DiagLog.i(TAG, "Tonight insight is disabled; skipping.")
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
            DiagLog.i(TAG, "Ignoring force refresh from a previous day (requested=$requestedEpochDay, today=${today.toEpochDay()}).")
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
            DiagLog.i(TAG, "Force refresh requested; bypassing today's cache.")
            null
        } else {
            runCatching { app.insightCache.forPeriodToday(today, period) }.getOrNull()
        }
        if (cached != null) {
            DiagLog.i(TAG, "Using cached $period insight for ${cached.forDate}.")
            return runCatching { deliver(cached, prefs, formatProse(cached, prefs)) }
                .map { Result.success() }
                .getOrElse {
                    DiagLog.e(TAG, "Cached delivery failed; falling through to fresh generate.", it)
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
                    .onFailure { DiagLog.w(TAG, "Severe alert notification failed for ${alert.event}.", it) }
            }
            runCatching { app.insightCache.store(insight) }
                .onSuccess {
                    // Push the fresh outfit out to any home-screen widgets.
                    // Gated on cache success because provideGlance() reads
                    // from the cache — kicking updateAll() after a failed
                    // write would just re-render the stale outfit. Failure
                    // here is non-blocking; the widget will catch up on the
                    // next successful fetch.
                    runCatching { OutfitWidget().updateAll(applicationContext) }
                        .onFailure { DiagLog.w(TAG, "Outfit widget update failed.", it) }
                }
                .onFailure { DiagLog.w(TAG, "Insight cache write failed; not blocking delivery.", it) }
            // Render once per delivery so notification, TTS, and the audit log
            // all share the same string and we don't reconfigure the
            // Configuration-overridden Resources three times per fire.
            val prose = formatProse(insight, prefs)
            deliver(insight, prefs, prose)
            DiagLog.i(TAG, "Insight delivered for ${insight.forDate}: $prose")
            Result.success()
        } catch (e: ResponseException) {
            // OpenMeteo 4xx → fail; 5xx → retry with backoff.
            val status = e.response.status
            if (status.value in 500..599) {
                DiagLog.w(TAG, "Server error $status from OpenMeteo; retrying.")
                Result.retry()
            } else {
                DiagLog.e(TAG, "Unexpected HTTP status $status from OpenMeteo", e)
                Result.failure(reason(REASON_UNEXPECTED_HTTP, "$status"))
            }
        } catch (e: ConnectTimeoutException) {
            DiagLog.w(TAG, "Connect timeout; retrying.", e); Result.retry()
        } catch (e: SocketTimeoutException) {
            DiagLog.w(TAG, "Socket timeout; retrying.", e); Result.retry()
        } catch (e: HttpRequestTimeoutException) {
            DiagLog.w(TAG, "Request timeout; retrying.", e); Result.retry()
        } catch (e: IOException) {
            DiagLog.w(TAG, "Network IO failure; retrying.", e); Result.retry()
        } catch (e: NoTransformationFoundException) {
            // Belt-and-braces for OpenMeteoClient's expectSuccess=true: the
            // gateway occasionally returns a 5xx with a text/html error page,
            // and if the response validator ever doesn't fire (R8 quirk, an
            // un-flagged call site) the JSON deserializer throws this instead
            // of ResponseException. Treat as transient and retry — the
            // alternative is the cryptic Ktor message landing on the failure
            // card.
            DiagLog.w(TAG, "Content-type mismatch from upstream (likely 5xx HTML body); retrying.", e)
            Result.retry()
        } catch (t: Throwable) {
            DiagLog.e(TAG, "Unhandled error; failing.", t)
            Result.failure(reason(REASON_UNHANDLED, summarize(t)))
        }
    }

    private fun reason(code: String, detail: String? = null) =
        if (detail.isNullOrBlank()) workDataOf(KEY_REASON to code)
        else workDataOf(KEY_REASON to code, KEY_REASON_DETAIL to detail)

    // First line of the exception message only — Ktor's NoTransformationFoundException
    // packs the URL, body excerpt, and a FAQ link into a multi-line wall of text.
    // Full stack trace stays in logcat and the diag ring buffer (DiagLog.e above).
    private fun summarize(t: Throwable): String {
        val firstLine = t.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        val joined = if (firstLine.isNullOrEmpty()) t.javaClass.simpleName
        else "${t.javaClass.simpleName}: $firstLine"
        return if (joined.length <= MAX_DETAIL_LEN) joined else joined.take(MAX_DETAIL_LEN - 1) + "…"
    }

    private suspend fun resolveLocation(prefs: UserPreferences): Location {
        if (prefs.useDeviceLocation) {
            val device = runCatching { app.locationResolver.resolve() }.getOrNull()
            if (device != null) {
                DiagLog.i(TAG, "Using device-resolved location at ${device.latitude}, ${device.longitude}.")
                return device
            }
            DiagLog.i(TAG, "Device location unavailable; falling back to settings location.")
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
     * Tonight delivery honours the user's tonight-specific [DeliveryMode]
     * (the night card has its own delivery selector, separate from the day
     * card's) and is also event-gated:
     *  - Notification posts only when the delivery mode includes notifications,
     *    matching today's behaviour. The notifier still picks the silent channel
     *    vs the default-priority channel based on whether there are calendar
     *    events tonight.
     *  - TTS only speaks when the delivery mode includes TTS *and* there are
     *    events tonight. If the evening is empty there's nothing to interrupt
     *    for, even on a TTS-enabled mode.
     */
    // TODO(brand-intro): consider prepending "Today's ClothesCast: " / "Tonight's ClothesCast: "
    // here once the voice preview's phrasing settles — see settings_tts_test_sample.
    private fun formatProse(insight: Insight, prefs: UserPreferences): String =
        InsightFormatter.forRegion(applicationContext, prefs.region).format(insight.summary)

    private suspend fun deliverTonight(insight: Insight, prefs: UserPreferences, prose: String) {
        val mode = prefs.tonightDeliveryMode
        val canNotify = mode == DeliveryMode.NOTIFICATION_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS
        // The notify-only-on-events toggle skips the notification entirely on
        // empty evenings — the cache is still written and the widget updated
        // upstream, so the user sees fresh state when they open the app. Only
        // log "skipping" when a notification would otherwise have posted.
        val skipEmptyEveningNotification = canNotify && prefs.tonightNotifyOnlyOnEvents && !insight.hasEvents
        if (skipEmptyEveningNotification) {
            DiagLog.i(TAG, "Tonight insight has no events and notify-only-on-events is on; skipping notification.")
        } else if (canNotify) {
            app.tonightInsightNotifier.notify(insight, prose)
        }
        if (!insight.hasEvents) {
            DiagLog.i(TAG, "Tonight insight has no events; skipping TTS.")
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
        withSpeechAudioFocus(applicationContext) {
            when (prefs.ttsEngine) {
                TtsEngine.GEMINI -> {
                    try {
                        GeminiTtsSpeaker(app.geminiTtsClient, voiceName = prefs.geminiVoice).speak(text, locale)
                        return@withSpeechAudioFocus
                    } catch (t: Throwable) {
                        DiagLog.w(TAG, "Gemini TTS failed; falling back to device TTS.", t)
                    }
                }
                TtsEngine.OPENAI -> {
                    try {
                        OpenAITtsSpeaker(app.openAiTtsClient, voice = prefs.openAiVoice).speak(text, locale)
                        return@withSpeechAudioFocus
                    } catch (t: Throwable) {
                        DiagLog.w(TAG, "OpenAI TTS failed; falling back to device TTS.", t)
                    }
                }
                TtsEngine.ELEVENLABS -> {
                    try {
                        ElevenLabsTtsSpeaker(app.elevenLabsTtsClient, voiceId = prefs.elevenLabsVoice).speak(text, locale)
                        return@withSpeechAudioFocus
                    } catch (t: Throwable) {
                        DiagLog.w(TAG, "ElevenLabs TTS failed; falling back to device TTS.", t)
                    }
                }
                TtsEngine.DEVICE -> Unit
            }
            try {
                app.deviceTtsSpeaker(prefs.deviceVoice).speak(text, locale)
            } catch (t: Throwable) {
                DiagLog.w(TAG, "Device TTS failed; insight is still posted as notification.", t)
            }
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

        // Cap unhandled-error detail so the "Show details" pane stays readable.
        private const val MAX_DETAIL_LEN = 240

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
