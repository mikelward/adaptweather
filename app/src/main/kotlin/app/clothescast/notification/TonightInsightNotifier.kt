package app.clothescast.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.clothescast.MainActivity
import app.clothescast.R
import app.clothescast.core.domain.model.Insight
import app.clothescast.insight.InsightFormatter

/**
 * Posts the tonight insight as a system notification. Picks one of two channels
 * based on whether the insight has calendar events tonight:
 *  - [CHANNEL_TONIGHT_INSIGHT_DEFAULT] when events are present — default importance,
 *    plays the user's notification sound. The user is heading out somewhere; the
 *    summary is worth interrupting them for.
 *  - [CHANNEL_TONIGHT_INSIGHT_SILENT] when the evening is empty — low importance,
 *    silent. Still posted so the user can glance at the lock screen and see the
 *    overnight insight, but nothing audible.
 *
 * Tapping the notification opens MainActivity. POST_NOTIFICATIONS is checked before
 * posting; on Android 13+ a missing permission silently no-ops (the insight is
 * still cached and surfaced in-app the next time the user opens it).
 */
class TonightInsightNotifier(
    private val context: Context,
    private val formatter: InsightFormatter = InsightFormatter(),
) {

    fun notify(insight: Insight) {
        if (!hasPostNotificationPermission()) return
        val prose = formatter.format(insight.summary)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val channel = if (insight.hasEvents) CHANNEL_TONIGHT_INSIGHT_DEFAULT else CHANNEL_TONIGHT_INSIGHT_SILENT
        val priority = if (insight.hasEvents) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(InsightNotifier.smallIconFor(insight.outfit?.top))
            .setLargeIcon(InsightNotifier.largeIconForTop(context, insight.outfit?.top))
            .setContentTitle(context.getString(R.string.notification_tonight_insight_title))
            .setContentText(prose)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prose))
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply {
                // Belt-and-braces: even if a downstream OEM ignores the silent
                // channel's importance, the per-notification flag still suppresses
                // sound + heads-up for the no-events case.
                if (!insight.hasEvents) setSilent(true)
            }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TONIGHT_INSIGHT, notification)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val NOTIFICATION_ID_TONIGHT_INSIGHT = 1003
        private const val REQUEST_OPEN_APP = 102
    }
}
