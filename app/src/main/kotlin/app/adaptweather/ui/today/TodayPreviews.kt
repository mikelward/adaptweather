package app.adaptweather.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.adaptweather.core.domain.model.ConfidenceInfo
import app.adaptweather.core.domain.model.ForecastConfidence
import app.adaptweather.core.domain.model.Insight
import app.adaptweather.ui.theme.AdaptWeatherTheme
import app.adaptweather.work.FetchAndNotifyWorker
import java.time.Instant
import java.time.LocalDate

//
// Preview wrappers for the Today-screen composables. Two purposes:
//
//   - Studio's design pane renders these via `@Preview` so designers/devs can
//     eyeball each state without running the app.
//   - The Roborazzi snapshot test in `app/src/test` invokes each function and
//     captures it to PNGs under `app/build/outputs/roborazzi/`, which CI
//     uploads as a workflow artifact for human review on every PR.
//
// Adding a new screen state: add a `@Preview internal fun XxxPreview()` here,
// and add a corresponding test method in `PreviewSnapshots` that calls it.
// The test list is explicit by design — no annotation scanner — so the set of
// captured artifacts is obvious from a single file.
//

@Composable
internal fun Frame(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    AdaptWeatherTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface { Column(modifier = Modifier.padding(16.dp)) { content() } }
    }
}

private val SAMPLE_INSIGHT = Insight(
    summary = "About 4° warmer than yesterday — comfortable in a sweater. Showers from late afternoon.",
    recommendedItems = listOf("jumper", "umbrella"),
    generatedAt = Instant.parse("2026-04-26T07:30:00Z"),
    forDate = LocalDate.of(2026, 4, 26),
)

@Preview(name = "Today · empty state", widthDp = 360)
@Composable
internal fun EmptyStatePreview() {
    Frame { EmptyState(onRefresh = {}) }
}

@Preview(name = "Today · insight loaded", widthDp = 360)
@Composable
internal fun InsightCardPreview() {
    Frame { InsightCard(SAMPLE_INSIGHT) }
}

@Preview(name = "Today · insight (dark)", widthDp = 360)
@Composable
internal fun InsightCardDarkPreview() {
    Frame(darkTheme = true) { InsightCard(SAMPLE_INSIGHT) }
}

@Preview(name = "Confidence · high", widthDp = 360)
@Composable
internal fun ConfidenceHighPreview() {
    Frame {
        ConfidenceChip(
            ConfidenceInfo(
                level = ForecastConfidence.HIGH,
                tempSpreadC = 0.8,
                precipSpreadPp = 5.0,
                modelsConsulted = listOf("ECMWF", "GFS", "ICON"),
            ),
        )
    }
}

@Preview(name = "Confidence · medium", widthDp = 360)
@Composable
internal fun ConfidenceMediumPreview() {
    Frame {
        ConfidenceChip(
            ConfidenceInfo(
                level = ForecastConfidence.MEDIUM,
                tempSpreadC = 2.5,
                precipSpreadPp = 20.0,
                modelsConsulted = listOf("ECMWF", "GFS"),
            ),
        )
    }
}

@Preview(name = "Confidence · low", widthDp = 360)
@Composable
internal fun ConfidenceLowPreview() {
    Frame {
        ConfidenceChip(
            ConfidenceInfo(
                level = ForecastConfidence.LOW,
                tempSpreadC = 6.1,
                precipSpreadPp = 55.0,
                modelsConsulted = listOf("ECMWF", "GFS", "ICON"),
            ),
        )
    }
}

@Preview(name = "Banner · running", widthDp = 360)
@Composable
internal fun WorkStatusRunningPreview() {
    Frame { WorkStatusBanner(WorkStatus.Running) }
}

@Preview(name = "Banner · failed (missing key)", widthDp = 360)
@Composable
internal fun WorkStatusFailedPreview() {
    Frame {
        WorkStatusBanner(
            WorkStatus.Failed(
                reason = FetchAndNotifyWorker.REASON_MISSING_API_KEY,
                detail = null,
            ),
        )
    }
}
