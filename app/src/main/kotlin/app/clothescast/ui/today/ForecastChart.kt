package app.clothescast.ui.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.toUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore

/**
 * Renders today's hourly temperature as a single line — feels-like or raw 2 m
 * air, controlled by [showFeelsLike]. Defaults to feels-like because that's
 * what the clothes rules and band sentence ("Today will be cool to mild")
 * are evaluated against; surfacing the raw line by default invited "which
 * line is which?" confusion. The parent toggles which series is shown.
 *
 * The hourly list is sourced from the cached Insight, populated by the morning
 * worker. When the cache predates this feature, [hourly] is empty and the chart
 * hides itself — the next worker run will fill it in.
 *
 * Underlying values are always Celsius (that's what HourlyForecast carries);
 * we convert at the edge with [toUnit] so the chart matches the user's
 * temperatureUnit preference. The legend, rendered by the parent, carries the
 * unit symbol — the axis stays unitless to avoid "10°C, 15°C, 20°C" repetition.
 */
@Composable
fun ForecastChart(
    hourly: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    showFeelsLike: Boolean,
    modifier: Modifier = Modifier,
) {
    if (hourly.isEmpty()) return

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hourly, temperatureUnit, showFeelsLike) {
        producer.runTransaction {
            lineSeries {
                val pick: (HourlyForecast) -> Double =
                    if (showFeelsLike) { h -> h.feelsLikeC } else { h -> h.temperatureC }
                series(hourly.map { pick(it).toUnit(temperatureUnit) })
            }
        }
    }

    val bottomFormatter = remember(hourly) {
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt().coerceIn(0, hourly.lastIndex)
            "%02d".format(hourly[idx].time.hour)
        }
    }

    // Vico's default rangeProvider clamps minY toward 0, so on a Fahrenheit day
    // with feels-like 52–62°F the axis spans 0–62 and the auto step-picker —
    // forced to fit that 62-unit range into ~3 label slots — lands on step 31,
    // giving a useless "0, 31, 62" axis. (Celsius hides this because 6–18 is
    // a smaller absolute range, so 0–18 with step 3 still looks fine.) Tighten
    // the y-range to both lines' actual min/max — both, not just the visible
    // line, so the axis doesn't shift when the user toggles between feels-like
    // and air. With a tight range the auto-stepper picks sensible increments
    // regardless of unit.
    val rangeProvider = remember(hourly, temperatureUnit) {
        val all = hourly.flatMap {
            listOf(it.feelsLikeC.toUnit(temperatureUnit), it.temperatureC.toUnit(temperatureUnit))
        }
        val dataMin = all.min()
        val dataMax = all.max()
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = dataMin
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = dataMax
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = rangeProvider),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = producer,
        // Vico's default initial zoom is `max(fixed, content)`, which on a 24-point
        // hourly series renders only the first ~10 hours and hides the rest behind
        // a scroll. Force-fit instead so the full day is visible at a glance — this
        // is a glanceable summary card, not an interactive explorer.
        zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
