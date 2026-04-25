package com.adaptweather.work

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
import com.adaptweather.AdaptWeatherApplication
import com.adaptweather.core.data.insight.GeminiBlockedException
import com.adaptweather.core.data.insight.GeminiEmptyResponseException
import com.adaptweather.core.data.insight.MissingApiKeyException
import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.Insight
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.UserPreferences
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Performs one daily fetch + insight + notify cycle. Runs in a WorkManager
 * OneTimeWorkRequest enqueued by AlarmReceiver, with NetworkType.CONNECTED and
 * exponential backoff so transient failures (no Wi-Fi at 7am) retry on next connectivity.
 *
 * Outcomes:
 * - Result.success — insight posted (or notification permission denied; we still cached the insight).
 * - Result.retry — transient network / 5xx; WorkManager will retry with backoff.
 * - Result.failure — non-recoverable: missing key, blocked by safety, or 4xx auth.
 *   Caller (the user) is expected to fix the configuration; no point retrying.
 *
 * Location for v1 is hard-coded to London. The plan calls for device GPS at notify time,
 * which lands in a follow-up alongside the location-permission UX.
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

        val location = resolveLocation()
        val languageTag = java.util.Locale.getDefault().toLanguageTag()

        return try {
            val insight = app.generateDailyInsight(location, prefs, languageTag)
            deliver(insight, prefs)
            Log.i(TAG, "Insight delivered for ${insight.forDate}: ${insight.summary}")
            Result.success()
        } catch (e: MissingApiKeyException) {
            Log.w(TAG, "No Gemini API key configured; user must set one in Settings.")
            Result.failure()
        } catch (e: GeminiBlockedException) {
            Log.w(TAG, "Gemini refused the prompt: ${e.message}")
            Result.failure()
        } catch (e: GeminiEmptyResponseException) {
            Log.w(TAG, "Gemini returned no candidate text; will retry.")
            Result.retry()
        } catch (e: ResponseException) {
            // 4xx auth/quota: don't retry. 5xx server-side: retry with backoff.
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                Log.w(TAG, "Auth failed against Gemini ($status); user must fix the key.")
                Result.failure()
            } else if (status.value in 500..599) {
                Log.w(TAG, "Server error $status; retrying.")
                Result.retry()
            } else {
                Log.e(TAG, "Unexpected HTTP status $status", e)
                Result.failure()
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
            Result.failure()
        }
    }

    private suspend fun deliver(insight: Insight, prefs: UserPreferences) {
        val mode = prefs.deliveryMode
        if (mode == DeliveryMode.NOTIFICATION_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            app.insightNotifier.notify(insight)
        }
        if (mode == DeliveryMode.TTS_ONLY || mode == DeliveryMode.NOTIFICATION_AND_TTS) {
            try {
                app.ttsSpeaker.speak(insight.summary)
            } catch (t: Throwable) {
                // TTS engine failures shouldn't lose the insight; the notification path
                // (when also enabled) already covered the user-visible delivery.
                Log.w(TAG, "TTS playback failed; insight is still posted as notification.", t)
            }
        }
    }

    private fun resolveLocation(): Location {
        // TODO: replace with the GPS-at-notify-time resolver once the location-permission
        // UX lands. London is a sensible v1 default and lets the rest of the pipeline run.
        return Location(latitude = 51.5074, longitude = -0.1278, displayName = "London")
    }

    companion object {
        private const val TAG = "FetchAndNotifyWorker"
        const val UNIQUE_WORK_NAME = "daily_insight_fetch"

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
