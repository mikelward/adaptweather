package app.clothescast.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import app.clothescast.MainActivity
import app.clothescast.R
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
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

        // The small status-bar icon mirrors the recommended top (silhouette).
        // The large icon picks up the same top in full colour so the expanded
        // notification carries the same glanceable "what to wear today" cue the
        // Today screen's OutfitPreviewCard does.
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSIGHT)
            .setSmallIcon(smallIconFor(insight.outfit?.top))
            .setLargeIcon(largeIconForTop(context, insight.outfit?.top))
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

        // The status-bar silhouette mirrors the recommended top so a glance at the
        // notification shade already says "sweater day" / "jacket day" before the
        // user reads anything. ic_notification_insight is itself a t-shirt silhouette,
        // so it doubles as both the TSHIRT case and the null fallback (older cached
        // insights without an outfit, weather alerts).
        internal fun smallIconFor(top: OutfitSuggestion.Top?): Int = when (top) {
            OutfitSuggestion.Top.SWEATER -> R.drawable.ic_notification_top_sweater
            OutfitSuggestion.Top.THICK_JACKET -> R.drawable.ic_notification_top_thick_jacket
            OutfitSuggestion.Top.TSHIRT, null -> R.drawable.ic_notification_insight
        }

        /**
         * Renders the recommended top as a Bitmap for [NotificationCompat.Builder.setLargeIcon].
         * Reuses the full-colour `ic_outfit_tshirt` / `ic_outfit_sweater` /
         * `ic_outfit_thick_jacket` drawables from `OutfitPreviewCard` so the
         * notification visual matches the home-screen card. Returns null when the
         * outfit is missing (older cached payloads), letting the system fall back
         * to no large icon.
         */
        internal fun largeIconForTop(context: Context, top: OutfitSuggestion.Top?): Bitmap? {
            val drawableRes = largeTopDrawable(top) ?: return null
            val drawable = ResourcesCompat.getDrawable(context.resources, drawableRes, context.theme)
                ?: return null
            val sizePx = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                .takeIf { it > 0 }
                ?: LARGE_ICON_FALLBACK_PX
            return drawable.toBitmap(width = sizePx, height = sizePx)
        }

        // Fallback large-icon size in raw pixels for the rare case where
        // `android.R.dimen.notification_large_icon_width` resolves to ≤0 (some
        // Robolectric configs and a handful of stripped-down OEM ROMs do this).
        // 192px ≈ 64dp on xxhdpi, which is the recommended large-icon target.
        private const val LARGE_ICON_FALLBACK_PX = 192

        @DrawableRes
        private fun largeTopDrawable(top: OutfitSuggestion.Top?): Int? = when (top) {
            OutfitSuggestion.Top.TSHIRT -> R.drawable.ic_outfit_tshirt
            OutfitSuggestion.Top.SWEATER -> R.drawable.ic_outfit_sweater
            OutfitSuggestion.Top.THICK_JACKET -> R.drawable.ic_outfit_thick_jacket
            null -> null
        }
    }
}
