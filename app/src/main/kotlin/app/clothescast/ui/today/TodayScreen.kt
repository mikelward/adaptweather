package app.clothescast.ui.today

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clothescast.R
import app.clothescast.core.domain.model.ConfidenceInfo
import app.clothescast.core.domain.model.ForecastConfidence
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.symbol
import app.clothescast.core.domain.model.toUnit
import app.clothescast.work.FetchAndNotifyWorker
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isWorking = state.workStatus is WorkStatus.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    // While the worker is enqueued or running we disable Refresh and swap
                    // the icon for a spinner. The work makes a billed Gemini insight call
                    // and (depending on the engine) a billed TTS call; re-tapping Refresh
                    // while one is in flight uses ExistingWorkPolicy.REPLACE, which kills
                    // the in-flight worker and starts another — re-issuing both requests.
                    // Disabling the button removes the foot-gun.
                    IconButton(
                        onClick = { triggerRefresh(context) },
                        enabled = !isWorking,
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.today_refresh),
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.today_open_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        TodayContent(
            state = state,
            padding = padding,
            isWorking = isWorking,
            onRefresh = { triggerRefresh(context) },
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayState,
    padding: PaddingValues,
    isWorking: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkStatusBanner(status = state.workStatus)
        if (state.insight == null) {
            EmptyState(onRefresh = onRefresh, isWorking = isWorking)
        } else {
            state.insight.outfit?.let { OutfitPreviewCard(it) }
            InsightCard(state.insight)
            if (state.insight.hourly.isNotEmpty()) {
                ForecastCard(state.insight.hourly, state.temperatureUnit)
            }
        }
    }
}

@Composable
internal fun WorkStatusBanner(status: WorkStatus) {
    when (status) {
        is WorkStatus.Idle -> Unit
        is WorkStatus.Running -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.today_working),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        is WorkStatus.Failed -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.today_failed_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = describeFailure(status),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun describeFailure(failed: WorkStatus.Failed): String {
    val message = when (failed.reason) {
        FetchAndNotifyWorker.REASON_UNEXPECTED_HTTP ->
            stringResource(R.string.today_failed_unexpected_http)
        FetchAndNotifyWorker.REASON_UNHANDLED, null ->
            stringResource(R.string.today_failed_unhandled)
        else -> failed.reason
    }
    return if (failed.detail.isNullOrBlank()) message else "$message (${failed.detail})"
}

@Composable
internal fun EmptyState(onRefresh: () -> Unit, isWorking: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.today_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRefresh, enabled = !isWorking) {
                Text(stringResource(R.string.today_fetch_now))
            }
        }
    }
}

/**
 * Glanceable "What to wear" card. Shows the top + bottom as flat-colour GNOME-style
 * icons stacked vertically — shirt above pants — so the pair reads as a head-to-toe
 * outfit instead of two unrelated items. Icons are fixed-colour SVGs (not Material-themed)
 * so each garment stays recognisable in light or dark mode.
 */
@Composable
internal fun OutfitPreviewCard(outfit: OutfitSuggestion) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.today_outfit_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Image(
                painter = painterResource(id = topIconRes(outfit.top)),
                contentDescription = stringResource(topLabelRes(outfit.top)),
                modifier = Modifier.height(112.dp),
            )
            Image(
                painter = painterResource(id = bottomIconRes(outfit.bottom)),
                contentDescription = stringResource(bottomLabelRes(outfit.bottom)),
                modifier = Modifier.height(112.dp),
            )
            Text(
                text = stringResource(topLabelRes(outfit.top)) +
                    " · " +
                    stringResource(bottomLabelRes(outfit.bottom)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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

@Composable
internal fun InsightCard(insight: Insight) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = DATE_FORMAT.format(insight.forDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.confidence?.let { ConfidenceChip(it) }
            Text(
                text = insight.summary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.today_generated_at,
                    GENERATED_AT_FORMAT.format(insight.generatedAt.atZone(ZoneId.systemDefault())),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact "How sure are we?" chip rendered inside [InsightCard]. Background color
 * tracks the level so the user gets the cue at a glance — green-ish for HIGH,
 * neutral for MEDIUM, error-tinted for LOW. Detail text shows the actual spread
 * across the consulted models so the user can judge for themselves.
 */
@Composable
internal fun ConfidenceChip(info: ConfidenceInfo) {
    val (bgColor, fgColor) = when (info.level) {
        ForecastConfidence.HIGH -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        ForecastConfidence.MEDIUM -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        ForecastConfidence.LOW -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    val labelRes = when (info.level) {
        ForecastConfidence.HIGH -> R.string.today_confidence_high
        ForecastConfidence.MEDIUM -> R.string.today_confidence_medium
        ForecastConfidence.LOW -> R.string.today_confidence_low
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = fgColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(
                    R.string.today_confidence_spread,
                    info.tempSpreadC,
                    info.precipSpreadPp,
                    info.modelsConsulted.size,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ForecastCard(hourly: List<HourlyForecast>, temperatureUnit: TemperatureUnit) {
    // Default to feels-like — matches the band classification the user sees in the
    // summary sentence. Tap anywhere on the card to flip to raw 2 m air, which is
    // typically what other weather apps lead with. We surface min/max for both
    // either way so the comparison is always visible without a tap.
    var showFeelsLike by rememberSaveable { mutableStateOf(true) }

    val symbol = temperatureUnit.symbol()
    val feelsLikeMinMax = remember(hourly, temperatureUnit) {
        formatMinMax(hourly.map { it.feelsLikeC }, temperatureUnit)
    }
    val airMinMax = remember(hourly, temperatureUnit) {
        formatMinMax(hourly.map { it.temperatureC }, temperatureUnit)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFeelsLike = !showFeelsLike },
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.today_forecast_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (feelsLikeMinMax != null && airMinMax != null) {
                Text(
                    text = stringResource(
                        R.string.today_forecast_min_max,
                        feelsLikeMinMax.first, feelsLikeMinMax.second, symbol,
                        airMinMax.first, airMinMax.second, symbol,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ForecastChart(
                hourly = hourly,
                temperatureUnit = temperatureUnit,
                showFeelsLike = showFeelsLike,
            )
            val legendRes = if (showFeelsLike) {
                R.string.today_forecast_legend_feels_like
            } else {
                R.string.today_forecast_legend_air
            }
            Text(
                text = stringResource(legendRes, symbol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMinMax(values: List<Double>, unit: TemperatureUnit): Pair<Int, Int>? {
    if (values.isEmpty()) return null
    val converted = values.map { it.toUnit(unit) }
    return converted.min().roundToInt() to converted.max().roundToInt()
}

private fun triggerRefresh(context: android.content.Context) {
    // force=true so an explicit user tap bypasses the same-day cache and
    // actually regenerates. Without this, Refresh on the same calendar day
    // just redelivers the morning's payload — surprising when the user has
    // changed wardrobe rules, location, or the underlying forecast has moved.
    FetchAndNotifyWorker.enqueueOneShot(context.applicationContext, force = true)
    Toast.makeText(
        context,
        context.getString(R.string.today_refresh_toast),
        Toast.LENGTH_SHORT,
    ).show()
}

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
private val GENERATED_AT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
