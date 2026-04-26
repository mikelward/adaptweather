package app.adaptweather.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.adaptweather.MainActivity
import app.adaptweather.R
import app.adaptweather.core.domain.model.WeatherAlert

/**
 * Posts a SEVERE / EXTREME weather alert as a separate, max-importance notification.
 * Each alert gets its own notification ID (derived from event + onset) so distinct
 * concurrent alerts stack rather than collapsing onto a single line.
 *
 * Permission semantics mirror [InsightNotifier]: a missing POST_NOTIFICATIONS grant
 * silently no-ops — the alert is already cached on the bundle and the user will see
 * it the next time they open the app.
 */
class WeatherAlertNotifier(private val context: Context) {

    fun notify(alert: WeatherAlert) {
        if (!hasPostNotificationPermission()) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = alert.headline ?: alert.description ?: alert.event
        val title = context.getString(R.string.notification_weather_alert_title, alert.event)

        val notification = NotificationCompat.Builder(context, CHANNEL_WEATHER_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_insight)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationIdFor(alert), notification)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationIdFor(alert: WeatherAlert): Int {
        // Stable ID per (event, onset) so re-posting the same alert updates in place,
        // while distinct alerts stack. Offset into the alert ID space to avoid collision
        // with the daily insight notification.
        val raw = (alert.event + alert.onset.toString()).hashCode()
        return NOTIFICATION_ID_BASE + (raw and 0x7FFFFF)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 2000
        private const val REQUEST_OPEN_APP = 200
    }
}
