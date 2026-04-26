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

/**
 * Posts a notification when the daily worker can't run because no API key is
 * configured. Without this the worker fails silently — the user wakes up to no
 * insight and no explanation. Reuses the daily-insight channel so the user can
 * mute or unmute both via the same toggle.
 */
class MissingApiKeyNotifier(private val context: Context) {

    // Named `post`, not `notify`, because a no-arg `notify()` would clash with
    // java.lang.Object.notify() — Kotlin flags it as an accidental override.
    fun post() {
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

        val text = context.getString(R.string.notification_missing_api_key_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSIGHT)
            .setSmallIcon(R.drawable.ic_notification_insight)
            .setContentTitle(context.getString(R.string.notification_missing_api_key_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MISSING_API_KEY, notification)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val NOTIFICATION_ID_MISSING_API_KEY = 1002
        private const val REQUEST_OPEN_APP = 102
    }
}
