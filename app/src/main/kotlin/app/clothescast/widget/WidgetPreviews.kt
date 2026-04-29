package app.clothescast.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.clothescast.R
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.ui.theme.ClothesCastTheme

//
// Compose stand-ins for the Glance OutfitWidget, used purely for snapshotting.
// Glance composables can't be rendered by Roborazzi (they emit RemoteViews, not
// Compose UI), so the widget layout is mirrored here in vanilla Compose at the
// same dimensions and styling. Mirrors the NotificationIconPreviews approach.
//
// Visual changes to OutfitWidget.kt should be reflected here so the snapshots
// stay representative — the two files are coupled by intent.
//
// The Frame matches a typical 2x2 launcher cell (~110dp). The widget itself
// resizes; this preview pins one realistic size so PR diffs surface layout
// regressions at the size most users will see.
//

private val WidgetWidth = 160.dp
private val WidgetHeight = 160.dp

@Composable
private fun WidgetFrame(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    ClothesCastTheme(darkTheme = darkTheme, dynamicColor = false) {
        // Slight surface inset so the rounded widget corners are visible against
        // a launcher-like backdrop. The actual launcher applies its own
        // wallpaper, so the colour here is just for visual contrast.
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun WidgetSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(width = WidgetWidth, height = WidgetHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
internal fun OutfitWidgetMockFilled(
    period: ForecastPeriod,
    outfit: OutfitSuggestion,
) {
    WidgetSurface {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(periodLabelResMock(period)),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Image(
                painter = painterResource(id = topIconResMock(outfit.top)),
                contentDescription = stringResource(topLabelResMock(outfit.top)),
                modifier = Modifier.size(48.dp),
            )
            Image(
                painter = painterResource(id = bottomIconResMock(outfit.bottom)),
                contentDescription = stringResource(bottomLabelResMock(outfit.bottom)),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(topLabelResMock(outfit.top)) +
                    " · " +
                    stringResource(bottomLabelResMock(outfit.bottom)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun OutfitWidgetMockEmpty() {
    WidgetSurface {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.widget_empty_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.widget_empty_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Preview(name = "Widget · today · t-shirt + shorts", widthDp = 192, heightDp = 192)
@Composable
internal fun WidgetTodayTShirtShortsPreview() {
    WidgetFrame {
        OutfitWidgetMockFilled(
            period = ForecastPeriod.TODAY,
            outfit = OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.SHORTS),
        )
    }
}

@Preview(name = "Widget · tonight · sweater + long pants", widthDp = 192, heightDp = 192)
@Composable
internal fun WidgetTonightSweaterPantsPreview() {
    WidgetFrame {
        OutfitWidgetMockFilled(
            period = ForecastPeriod.TONIGHT,
            outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
        )
    }
}

@Preview(name = "Widget · today · thick jacket + long pants", widthDp = 192, heightDp = 192)
@Composable
internal fun WidgetTodayJacketPantsPreview() {
    WidgetFrame {
        OutfitWidgetMockFilled(
            period = ForecastPeriod.TODAY,
            outfit = OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.LONG_PANTS),
        )
    }
}

@Preview(name = "Widget · tonight (dark)", widthDp = 192, heightDp = 192)
@Composable
internal fun WidgetTonightDarkPreview() {
    WidgetFrame(darkTheme = true) {
        OutfitWidgetMockFilled(
            period = ForecastPeriod.TONIGHT,
            outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
        )
    }
}

@Preview(name = "Widget · empty", widthDp = 192, heightDp = 192)
@Composable
internal fun WidgetEmptyPreview() {
    WidgetFrame { OutfitWidgetMockEmpty() }
}

private fun periodLabelResMock(period: ForecastPeriod): Int = when (period) {
    ForecastPeriod.TODAY -> R.string.today_outfit_label_today
    ForecastPeriod.TONIGHT -> R.string.today_outfit_label_tonight
}

private fun topIconResMock(top: OutfitSuggestion.Top): Int = when (top) {
    OutfitSuggestion.Top.TSHIRT -> R.drawable.ic_outfit_tshirt
    OutfitSuggestion.Top.SWEATER -> R.drawable.ic_outfit_sweater
    OutfitSuggestion.Top.THICK_JACKET -> R.drawable.ic_outfit_thick_jacket
}

private fun topLabelResMock(top: OutfitSuggestion.Top): Int = when (top) {
    OutfitSuggestion.Top.TSHIRT -> R.string.today_outfit_top_tshirt
    OutfitSuggestion.Top.SWEATER -> R.string.today_outfit_top_sweater
    OutfitSuggestion.Top.THICK_JACKET -> R.string.today_outfit_top_thick_jacket
}

private fun bottomIconResMock(bottom: OutfitSuggestion.Bottom): Int = when (bottom) {
    OutfitSuggestion.Bottom.SHORTS -> R.drawable.ic_outfit_shorts
    OutfitSuggestion.Bottom.LONG_PANTS -> R.drawable.ic_outfit_long_pants
}

private fun bottomLabelResMock(bottom: OutfitSuggestion.Bottom): Int = when (bottom) {
    OutfitSuggestion.Bottom.SHORTS -> R.string.today_outfit_bottom_shorts
    OutfitSuggestion.Bottom.LONG_PANTS -> R.string.today_outfit_bottom_long_pants
}
