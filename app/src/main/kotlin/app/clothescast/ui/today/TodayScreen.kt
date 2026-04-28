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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.symbol
import app.clothescast.core.domain.model.toUnit
import app.clothescast.diag.BugReport
import app.clothescast.diag.findActivity
import app.clothescast.insight.InsightFormatter
import app.clothescast.ui.settings.resourcesLocale
import app.clothescast.work.FetchAndNotifyWorker
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val coroutineScope = rememberCoroutineScope()
    val isWorking = state.workStatus is WorkStatus.Running
    var overflowExpanded by remember { mutableStateOf(false) }

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
                        onClick = { triggerRefresh(context, state.morningTime, state.tonightTime) },
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
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.today_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_report_a_bug)) },
                            onClick = {
                                overflowExpanded = false
                                if (activity != null) {
                                    coroutineScope.launch {
                                        BugReport.share(activity, includeScreenshot = true)
                                    }
                                }
                            },
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
            onRefresh = { triggerRefresh(context, state.morningTime, state.tonightTime) },
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
            OutfitPreviewRow(state.insight)
            InsightCard(state.insight, state.region)
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
                    if (!status.detail.isNullOrBlank()) {
                        var showDetails by rememberSaveable(status.detail) { mutableStateOf(false) }
                        Text(
                            text = stringResource(
                                if (showDetails) R.string.today_failed_hide_details
                                else R.string.today_failed_show_details,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { showDetails = !showDetails },
                        )
                        if (showDetails) {
                            Text(
                                text = status.detail,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun describeFailure(failed: WorkStatus.Failed): String =
    when (failed.reason) {
        FetchAndNotifyWorker.REASON_UNEXPECTED_HTTP ->
            stringResource(R.string.today_failed_unexpected_http)
        FetchAndNotifyWorker.REASON_UNHANDLED, null ->
            stringResource(R.string.today_failed_unhandled)
        else -> failed.reason
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
 * Side-by-side "What to wear" row. Shows the primary outfit on the left and the
 * upcoming-period outfit on the right — "Today + Tonight" on a morning insight,
 * "Tonight + Tomorrow" on an evening one — so a glance covers both the next few
 * hours and the next handover (heading-out outfit + coming-home outfit).
 *
 * Falls back to a single card when the insight didn't carry a [Insight.nextOutfit]
 * (legacy cache payloads, or a tonight insight on a forecast bundle without
 * tomorrow's daily aggregates).
 *
 * TODO(outfit-weather-overlay): place a small weather glyph (sun / cloud / rain /
 *   snow) over the centre of the top icon so a glance carries both "what to wear"
 *   *and* "what's it doing outside" — e.g. a t-shirt with a sun, a sweater with a
 *   raincloud. Use the same imagery for the product launcher icon (mipmap/ic_launcher,
 *   ic_launcher_round, ic_launcher_background) so the home-screen icon, the
 *   outfit cards, and the notification large icon all read as one family.
 */
@Composable
internal fun OutfitPreviewRow(insight: Insight) {
    val primary = insight.outfit ?: return
    val (primaryLabel, nextLabel) = outfitLabels(insight.period)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutfitPreviewCard(
            outfit = primary,
            label = stringResource(primaryLabel),
            modifier = Modifier.weight(1f),
        )
        insight.nextOutfit?.let {
            OutfitPreviewCard(
                outfit = it,
                label = stringResource(nextLabel),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun outfitLabels(period: ForecastPeriod): Pair<Int, Int> = when (period) {
    ForecastPeriod.TODAY -> R.string.today_outfit_label_today to R.string.today_outfit_label_tonight
    ForecastPeriod.TONIGHT -> R.string.today_outfit_label_tonight to R.string.today_outfit_label_tomorrow
}

@Composable
internal fun OutfitPreviewCard(
    outfit: OutfitSuggestion,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = topIconRes(outfit.top)),
                    contentDescription = stringResource(topLabelRes(outfit.top)),
                    modifier = Modifier.height(96.dp),
                )
                Image(
                    painter = painterResource(id = bottomIconRes(outfit.bottom)),
                    contentDescription = stringResource(bottomLabelRes(outfit.bottom)),
                    modifier = Modifier.height(96.dp),
                )
            }
            Text(
                text = stringResource(topLabelRes(outfit.top)) +
                    " · " +
                    stringResource(bottomLabelRes(outfit.bottom)),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
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
internal fun InsightCard(insight: Insight, region: Region) {
    val context = LocalContext.current
    val formatter = remember(context, region) { InsightFormatter.forRegion(context, region) }
    val locale = remember(context, region) {
        region.bcp47?.let { Locale.forLanguageTag(it) } ?: context.resourcesLocale()
    }
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    val timeFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = dateFormat.format(insight.forDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.confidence?.let { ConfidenceChip(it) }
            Text(
                text = formatter.format(insight.summary),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.today_generated_at,
                    timeFormat.format(insight.generatedAt.atZone(ZoneId.systemDefault())),
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

private fun triggerRefresh(
    context: android.content.Context,
    morningTime: java.time.LocalTime,
    tonightTime: java.time.LocalTime,
) {
    // force=true so an explicit user tap bypasses the same-day cache and
    // actually regenerates. Without this, Refresh on the same calendar day
    // just redelivers the morning's payload — surprising when the user has
    // changed clothes rules, location, or the underlying forecast has moved.
    //
    // Period follows wall-clock time so an evening tap inside the user's
    // tonight window regenerates the tonight insight — that's the one whose
    // primary outfit is "Tonight" and whose nextOutfit drives the "Tomorrow"
    // card. A morning tap regenerates today, whose nextOutfit drives the
    // "Tonight" card. Window boundaries come from the user's actual schedule
    // times (prefs.schedule.time / prefs.tonightSchedule.time) so a customised
    // schedule doesn't desync from the manual refresh.
    val period = if (java.time.LocalTime.now().isInTonightWindow(morningTime, tonightTime)) {
        ForecastPeriod.TONIGHT
    } else {
        ForecastPeriod.TODAY
    }
    FetchAndNotifyWorker.enqueueOneShot(context.applicationContext, force = true, period = period)
    val toastRes = when (period) {
        ForecastPeriod.TODAY -> R.string.today_refresh_toast_daily
        ForecastPeriod.TONIGHT -> R.string.today_refresh_toast_nightly
    }
    Toast.makeText(context, context.getString(toastRes), Toast.LENGTH_SHORT).show()
}

// [tonightTime] inclusive through [morningTime] exclusive — wraps midnight when
// tonightTime > morningTime (the normal case). When the user has crossed them
// (a tonight time earlier than morning, e.g. 06:30 / 07:00) the predicate
// degenerates to the in-between sliver, which is fine: the user's two slots
// effectively touch and either side of the line is reasonable.
private fun java.time.LocalTime.isInTonightWindow(
    morningTime: java.time.LocalTime,
    tonightTime: java.time.LocalTime,
): Boolean = if (tonightTime > morningTime) {
    this >= tonightTime || this < morningTime
} else {
    this >= tonightTime && this < morningTime
}

