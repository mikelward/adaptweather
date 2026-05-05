package app.clothescast.diag

import android.content.Context
import app.clothescast.data.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Bridges the user's Privacy → "Send crash + usage data" toggle to Firebase
 * Analytics + Crashlytics collection flags.
 *
 * The contract for what may / may not appear in those payloads is in
 * PRIVACY.md — calendar event data, location, insight prose, notification
 * text, and API keys are out of scope. This class deliberately does NOT set
 * Crashlytics custom keys from any of those: it only flips collection on or
 * off. Custom-event instrumentation is a follow-up.
 *
 * No-ops if Firebase didn't initialise — i.e. when this build was assembled
 * without `app/google-services.json` (CI). The .gitignore-d JSON is the only
 * thing keeping Firebase from auto-starting via FirebaseInitProvider, so
 * "no JSON, no SDK calls" is the natural quiet path. The Settings toggle
 * still flips the persisted preference in that case so a later build that
 * does have the JSON inherits the user's choice.
 */
object Telemetry {
    /**
     * Subscribes to [settings]'s telemetry preference and pushes each change
     * into FirebaseAnalytics + FirebaseCrashlytics. Both SDKs persist their
     * collection flag across launches, so a `false` set here also suppresses
     * the very first crash on next process start before this collector
     * attaches.
     */
    fun start(context: Context, settings: SettingsRepository, scope: CoroutineScope) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val analytics = FirebaseAnalytics.getInstance(context)
        val crashlytics = FirebaseCrashlytics.getInstance()
        scope.launch {
            settings.preferences
                .map { it.telemetryEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    analytics.setAnalyticsCollectionEnabled(enabled)
                    crashlytics.setCrashlyticsCollectionEnabled(enabled)
                }
        }
    }
}
