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
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/**
 * Renders today's hourly temperature and feels-like as two lines.
 *
 * The hourly list is sourced from the cached Insight, populated by the morning
 * worker. When the cache predates this feature, [hourly] is empty and the chart
 * hides itself — the next worker run will fill it in.
 *
 * Underlying values are always Celsius (that's what HourlyForecast carries);
 * we convert at the edge with [toUnit] so the chart matches the user's
 * temperatureUnit preference. The legend, rendered by the parent, carries the
 * unit symbol — the axis stays unitless to avoid "10°C, 15°C, 20°C" repetition.
 *
 * Series order:
 *   0 — raw 2 m air temperature
 *   1 — apparent / feels-like
 * Drawn in that order so feels-like sits on top, matching what the wardrobe
 * rules actually evaluate against.
 */
@Composable
fun ForecastChart(
    hourly: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    if (hourly.isEmpty()) return

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hourly, temperatureUnit) {
        producer.runTransaction {
            lineSeries {
                series(hourly.map { it.temperatureC.toUnit(temperatureUnit) })
                series(hourly.map { it.feelsLikeC.toUnit(temperatureUnit) })
            }
        }
    }

    val bottomFormatter = remember(hourly) {
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt().coerceIn(0, hourly.lastIndex)
            "%02d".format(hourly[idx].time.hour)
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = producer,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
