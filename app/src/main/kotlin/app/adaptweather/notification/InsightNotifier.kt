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
import app.adaptweather.core.domain.model.Insight

/**
 * Posts the daily insight as a system notification. Tapping the notification opens
 * MainActivity. POST_NOTIFICATIONS is checked before posting; on Android 13+ a missing
 * permission silently no-ops (the worker keeps the cached insight; the user will see it
 * in-app the next time they open the app).
 */
class InsightNotifier(private val context: Context) {

    fun notify(insight: Insight) {
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

        // TODO(notification-figure): when [insight.outfit] is non-null, render the
        // outfit icons (the same ic_outfit_* drawables the Today screen shows in
        // OutfitPreviewCard) into a Bitmap sized for Notification.LARGE_ICON_SIZE
        // and attach via .setLargeIcon(bitmap), so the notification carries the
        // same glanceable "what to wear today" cue without the user having to
        // open the app.
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSIGHT)
            .setSmallIcon(R.drawable.ic_notification_insight)
            .setContentTitle(context.getString(R.string.notification_daily_insight_title))
            .setContentText(insight.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(insight.summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY_INSIGHT, notification)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val NOTIFICATION_ID_DAILY_INSIGHT = 1001
        private const val REQUEST_OPEN_APP = 100
    }
}
