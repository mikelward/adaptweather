package app.clothescast.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.clothescast.ClothesCastApplication
import app.clothescast.MainActivity
import app.clothescast.R
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
import kotlinx.coroutines.flow.first

/**
 * A glanceable home-screen widget showing the current period's outfit — top icon,
 * bottom icon, and the period label ("Today" / "Tonight"). Tapping the widget
 * opens the app.
 *
 * The widget reads the same [app.clothescast.data.InsightCache] the Today screen
 * does, so it stays in lockstep with whatever the app last computed. Refreshes
 * are pushed by [app.clothescast.work.FetchAndNotifyWorker] after each cache
 * write via OutfitWidget().updateAll(context); there's no per-widget polling.
 */
class OutfitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ClothesCastApplication
        // Read once on each provideGlance() pass — the worker calls updateAll()
        // after writing to the cache, which re-invokes this function. The flow
        // emits the freshest of the two cached periods (TODAY / TONIGHT), which
        // is the same "what's relevant right now" choice the Today screen makes.
        val insight = runCatching { app.insightCache.latest.first() }.getOrNull()
        provideContent {
            GlanceTheme {
                OutfitWidgetContent(insight)
            }
        }
    }
}

@Composable
private fun OutfitWidgetContent(insight: Insight?) {
    val outfit = insight?.outfit
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (insight == null || outfit == null) {
            EmptyContent()
        } else {
            FilledContent(insight.period, outfit)
        }
    }
}

@Composable
private fun FilledContent(period: ForecastPeriod, outfit: OutfitSuggestion) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(periodLabelRes(period)),
            style = labelStyle(),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        // Top-over-bottom vertical stack matches the Today screen's
        // OutfitPreviewCard so the home-screen glance reads the same way as
        // the in-app card the user already knows.
        Image(
            provider = ImageProvider(topIconRes(outfit.top)),
            contentDescription = context.getString(topLabelRes(outfit.top)),
            modifier = GlanceModifier.size(48.dp),
        )
        Image(
            provider = ImageProvider(bottomIconRes(outfit.bottom)),
            contentDescription = context.getString(bottomLabelRes(outfit.bottom)),
            modifier = GlanceModifier.size(48.dp),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = context.getString(topLabelRes(outfit.top)) +
                " · " +
                context.getString(bottomLabelRes(outfit.bottom)),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_empty_title),
            style = labelStyle(),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = context.getString(R.string.widget_empty_subtitle),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun labelStyle(): TextStyle = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
)

private fun periodLabelRes(period: ForecastPeriod): Int = when (period) {
    ForecastPeriod.TODAY -> R.string.today_outfit_label_today
    ForecastPeriod.TONIGHT -> R.string.today_outfit_label_tonight
}

private fun topIconRes(top: OutfitSuggestion.Top): Int = when (top) {
    OutfitSuggestion.Top.TSHIRT -> R.drawable.ic_outfit_tshirt
    OutfitSuggestion.Top.SWEATER -> R.drawable.ic_outfit_sweater
    OutfitSuggestion.Top.THICK_JACKET -> R.drawable.ic_outfit_thick_jacket
}

private fun topLabelRes(top: OutfitSuggestion.Top): Int = when (top) {
    OutfitSuggestion.Top.TSHIRT -> R.string.today_outfit_top_tshirt
    OutfitSuggestion.Top.SWEATER -> R.string.today_outfit_top_sweater
    OutfitSuggestion.Top.THICK_JACKET -> R.string.today_outfit_top_thick_jacket
}

private fun bottomIconRes(bottom: OutfitSuggestion.Bottom): Int = when (bottom) {
    OutfitSuggestion.Bottom.SHORTS -> R.drawable.ic_outfit_shorts
    OutfitSuggestion.Bottom.LONG_PANTS -> R.drawable.ic_outfit_long_pants
}

private fun bottomLabelRes(bottom: OutfitSuggestion.Bottom): Int = when (bottom) {
    OutfitSuggestion.Bottom.SHORTS -> R.string.today_outfit_bottom_shorts
    OutfitSuggestion.Bottom.LONG_PANTS -> R.string.today_outfit_bottom_long_pants
}
