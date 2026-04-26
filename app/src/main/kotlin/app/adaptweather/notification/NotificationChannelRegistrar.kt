package app.adaptweather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import app.adaptweather.R

/**
 * Channel IDs are stable identifiers persisted by the system; renaming or deleting one
 * is a user-visible change. Bump the suffix when channel semantics change.
 */
internal const val CHANNEL_DAILY_INSIGHT = "daily_insight_v1"
internal const val CHANNEL_WEATHER_ALERTS = "weather_alerts_v1"

/**
 * Registers the notification channel(s) used by the app. Idempotent — safe to call from
 * Application.onCreate on every cold start.
 *
 * Two channels:
 * - **Daily insight** (HIGH): the morning weather summary the user opted in to.
 * - **Severe weather alerts** (MAX): out-of-band notifications for SEVERE / EXTREME
 *   alerts. Separate channel so the user can mute the daily summary without losing
 *   life-safety alerts (and vice-versa).
 */
object NotificationChannelRegistrar {
    fun register(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val daily = NotificationChannel(
            CHANNEL_DAILY_INSIGHT,
            context.getString(R.string.notification_channel_daily_insight_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_daily_insight_description)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val alerts = NotificationChannel(
            CHANNEL_WEATHER_ALERTS,
            context.getString(R.string.notification_channel_weather_alerts_name),
            NotificationManager.IMPORTANCE_MAX,
        ).apply {
            description = context.getString(R.string.notification_channel_weather_alerts_description)
            setShowBadge(true)
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(daily)
        manager.createNotificationChannel(alerts)
    }
}
