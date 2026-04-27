package app.clothescast.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.ConfidenceInfo
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastConfidence
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
import app.clothescast.core.domain.model.WeatherCondition
import app.clothescast.ui.theme.ClothesCastTheme
import app.clothescast.work.FetchAndNotifyWorker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
    ClothesCastTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface { Column(modifier = Modifier.padding(16.dp)) { content() } }
    }
}

private val SAMPLE_INSIGHT = Insight(
    summary = InsightSummary(
        period = ForecastPeriod.TODAY,
        band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD),
        delta = DeltaClause(4, DeltaClause.Direction.WARMER),
        wardrobe = WardrobeClause(listOf("jumper", "umbrella")),
        precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
    ),
    recommendedItems = listOf("jumper", "umbrella"),
    generatedAt = Instant.parse("2026-04-26T07:30:00Z"),
    forDate = LocalDate.of(2026, 4, 26),
)

@Preview(name = "Outfit · t-shirt + shorts", widthDp = 360)
@Composable
internal fun OutfitTShirtShortsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.SHORTS),
            label = "Today",
        )
    }
}

@Preview(name = "Outfit · t-shirt + long pants", widthDp = 360)
@Composable
internal fun OutfitTShirtPantsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.LONG_PANTS),
            label = "Today",
        )
    }
}

@Preview(name = "Outfit · sweater + shorts", widthDp = 360)
@Composable
internal fun OutfitSweaterShortsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.SHORTS),
            label = "Tonight",
        )
    }
}

@Preview(name = "Outfit · sweater + long pants", widthDp = 360)
@Composable
internal fun OutfitSweaterPantsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
            label = "Tonight",
        )
    }
}

@Preview(name = "Outfit · thick jacket + shorts", widthDp = 360)
@Composable
internal fun OutfitJacketShortsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.SHORTS),
            label = "Tomorrow",
        )
    }
}

@Preview(name = "Outfit · thick jacket + long pants", widthDp = 360)
@Composable
internal fun OutfitJacketPantsPreview() {
    Frame {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.LONG_PANTS),
            label = "Tomorrow",
        )
    }
}

@Preview(name = "Outfit · sweater + pants (dark)", widthDp = 360)
@Composable
internal fun OutfitSweaterPantsDarkPreview() {
    Frame(darkTheme = true) {
        OutfitPreviewCard(
            outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
            label = "Tonight",
        )
    }
}

@Preview(name = "Outfit row · today + tonight", widthDp = 360)
@Composable
internal fun OutfitRowTodayTonightPreview() {
    Frame {
        OutfitPreviewRow(
            SAMPLE_INSIGHT.copy(
                period = ForecastPeriod.TODAY,
                outfit = OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.SHORTS),
                nextOutfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
            ),
        )
    }
}

@Preview(name = "Outfit row · tonight + tomorrow", widthDp = 360)
@Composable
internal fun OutfitRowTonightTomorrowPreview() {
    Frame {
        OutfitPreviewRow(
            SAMPLE_INSIGHT.copy(
                period = ForecastPeriod.TONIGHT,
                outfit = OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS),
                nextOutfit = OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.LONG_PANTS),
            ),
        )
    }
}

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

@Preview(name = "Banner · failed (HTTP error)", widthDp = 360)
@Composable
internal fun WorkStatusFailedPreview() {
    Frame {
        WorkStatusBanner(
            WorkStatus.Failed(
                reason = FetchAndNotifyWorker.REASON_UNEXPECTED_HTTP,
                detail = "503",
            ),
        )
    }
}
