package com.adaptweather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.adaptweather.R

/**
 * Channel IDs are stable identifiers persisted by the system; renaming or deleting one
 * is a user-visible change. Bump the suffix when channel semantics change.
 */
internal const val CHANNEL_DAILY_INSIGHT = "daily_insight_v1"

/**
 * Registers the notification channel(s) used by the app. Idempotent — safe to call from
 * Application.onCreate on every cold start.
 *
 * The daily-insight channel is high-importance because the user explicitly opted in to a
 * morning notification, and the OS otherwise groups it silently if a lower importance is used.
 */
object NotificationChannelRegistrar {
    fun register(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val channel = NotificationChannel(
            CHANNEL_DAILY_INSIGHT,
            context.getString(R.string.notification_channel_daily_insight_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_daily_insight_description)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }
}
