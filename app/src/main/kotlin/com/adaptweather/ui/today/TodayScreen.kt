package com.adaptweather.ui.today

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptweather.R
import com.adaptweather.core.domain.model.ConfidenceInfo
import com.adaptweather.core.domain.model.ForecastConfidence
import com.adaptweather.core.domain.model.HourlyForecast
import com.adaptweather.core.domain.model.Insight
import com.adaptweather.work.FetchAndNotifyWorker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = { triggerRefresh(context) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.today_refresh),
                        )
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
        TodayContent(state = state, padding = padding, onRefresh = { triggerRefresh(context) })
    }
}

@Composable
private fun TodayContent(
    state: TodayState,
    padding: PaddingValues,
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
            EmptyState(onRefresh = onRefresh)
        } else {
            InsightCard(state.insight)
            if (state.insight.hourly.isNotEmpty()) {
                ForecastCard(state.insight.hourly)
            }
        }
    }
}

@Composable
private fun WorkStatusBanner(status: WorkStatus) {
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
        FetchAndNotifyWorker.REASON_MISSING_API_KEY ->
            stringResource(R.string.today_failed_missing_api_key)
        FetchAndNotifyWorker.REASON_GEMINI_AUTH ->
            stringResource(R.string.today_failed_gemini_auth)
        FetchAndNotifyWorker.REASON_GEMINI_BLOCKED ->
            stringResource(R.string.today_failed_gemini_blocked)
        FetchAndNotifyWorker.REASON_UNEXPECTED_HTTP ->
            stringResource(R.string.today_failed_unexpected_http)
        FetchAndNotifyWorker.REASON_UNHANDLED, null ->
            stringResource(R.string.today_failed_unhandled)
        else -> failed.reason
    }
    return if (failed.detail.isNullOrBlank()) message else "$message (${failed.detail})"
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
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
            Button(onClick = onRefresh) {
                Text(stringResource(R.string.today_fetch_now))
            }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
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
            if (insight.recommendedItems.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.today_recommended_items,
                        insight.recommendedItems.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun ConfidenceChip(info: ConfidenceInfo) {
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
private fun ForecastCard(hourly: List<HourlyForecast>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.today_forecast_title),
                style = MaterialTheme.typography.titleSmall,
            )
            ForecastChart(hourly = hourly)
            Text(
                text = stringResource(R.string.today_forecast_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun triggerRefresh(context: android.content.Context) {
    FetchAndNotifyWorker.enqueueOneShot(context.applicationContext)
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
