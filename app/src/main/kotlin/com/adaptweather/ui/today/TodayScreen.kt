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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.adaptweather.BuildConfig
import com.adaptweather.R
import com.adaptweather.core.domain.model.Insight
import com.adaptweather.work.FetchAndNotifyWorker
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
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
        TodayContent(state = state, padding = padding)
    }
}

@Composable
private fun TodayContent(state: TodayState, padding: PaddingValues) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.insight == null) {
            EmptyState(
                onFireNow = if (BuildConfig.DEBUG) {
                    {
                        FetchAndNotifyWorker.enqueueOneShot(context.applicationContext)
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_debug_fire_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    null
                },
            )
        } else {
            InsightCard(state.insight)
        }
    }
}

@Composable
private fun EmptyState(onFireNow: (() -> Unit)?) {
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
            if (onFireNow != null) {
                Button(onClick = onFireNow) {
                    Text(stringResource(R.string.settings_debug_fire_now))
                }
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

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
private val GENERATED_AT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
