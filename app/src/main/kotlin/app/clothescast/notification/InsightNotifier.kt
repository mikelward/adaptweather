package app.clothescast.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.clothescast.MainActivity
import app.clothescast.R
import app.clothescast.core.domain.model.Insight
import app.clothescast.insight.InsightFormatter

/**
 * Posts the daily insight as a system notification. Tapping the notification opens
 * MainActivity. POST_NOTIFICATIONS is checked before posting; on Android 13+ a missing
 * permission silently no-ops (the worker keeps the cached insight; the user will see it
 * in-app the next time they open the app).
 */
class InsightNotifier(
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

        // Diagnostic build: feed two visually distinct labelled bitmaps into
        // setSmallIcon and setLargeIcon so we can see on-device which API maps
        // to the left-hand avatar slot vs. any thumbnail / status-bar /
        // corner-badge slot. "S" on a red background = setSmallIcon. "L" on a
        // green background = setLargeIcon. Wherever each letter lands, that's
        // where the API rendered. Once eyeballed on Android 16 we'll drop the
        // losing API and put the real outfit drawable into the winner.
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSIGHT)
            .setSmallIcon(IconCompat.createWithBitmap(diagnosticBitmap("S", Color.RED)))
            .setLargeIcon(diagnosticBitmap("L", Color.rgb(0, 160, 0)))
            .setContentTitle(context.getString(R.string.notification_daily_insight_title))
            .setContentText(prose)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prose))
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

        /**
         * Builds a 192×192 solid-colour bitmap with [label] drawn in white in
         * the centre. Diagnostic-only — the labels let us tell on-device which
         * Notification API rendered which slot ("S" = small icon, "L" = large
         * icon), so we know where to put the real outfit drawable once the
         * avatar slot is identified.
         */
        internal fun diagnosticBitmap(label: String, backgroundColor: Int): Bitmap {
            val sizePx = 192
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 128f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(label, sizePx / 2f, baseline, paint)
            return bitmap
        }
    }
}
