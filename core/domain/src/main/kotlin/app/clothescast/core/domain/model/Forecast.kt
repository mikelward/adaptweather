package app.clothescast.core.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One day's forecast.
 *
 * Temperature is provided as both raw 2 m air temperature ([temperatureMinC],
 * [temperatureMaxC]) and apparent / "feels like" ([feelsLikeMinC], [feelsLikeMaxC]),
 * which factors in wind chill and humidity. The user-facing prompt and wardrobe
 * thresholds use feels-like — that's what people actually experience.
 */
data class DailyForecast(
    val date: LocalDate,
    val temperatureMinC: Double,
    val temperatureMaxC: Double,
    val feelsLikeMinC: Double,
    val feelsLikeMaxC: Double,
    val precipitationProbabilityMaxPct: Double,
    val precipitationMmTotal: Double,
    val condition: WeatherCondition,
    val hourly: List<HourlyForecast> = emptyList(),
)

data class HourlyForecast(
    val time: LocalTime,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val precipitationProbabilityPct: Double,
    val condition: WeatherCondition,
)

/** Coarse condition buckets sufficient for prompt construction and rule evaluation. */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
}

/**
 * Wall-clock boundary between the "daytime" and "evening" forecast windows. The
 * daily insight summary speaks for [DAY_START_HOUR, DAY_END_HOUR); anything past
 * that is the evening slice that piggy-backs on calendar events with a location.
 * 07:00 / 19:00 — early enough that the morning commute is covered, late enough
 * that an after-work dinner falls into the evening bucket where it belongs.
 */
const val DAY_START_HOUR: Int = 7
const val DAY_END_HOUR: Int = 19

/**
 * Returns a copy of this forecast restricted to hourly entries within
 * `[start, end)` (start-of-hour comparison), with feels-like / raw-temp / precip
 * aggregates recomputed over the slice. When [hourly] is empty or the slice is
 * empty, returns the original — fixtures and providers without hourly resolution
 * fall through unchanged so callers don't have to special-case them.
 */
internal fun DailyForecast.window(start: LocalTime, end: LocalTime): DailyForecast {
    if (hourly.isEmpty()) return this
    val slice = hourly.filter { !it.time.isBefore(start) && it.time.isBefore(end) }
    if (slice.isEmpty()) return this
    return copy(
        temperatureMinC = slice.minOf { it.temperatureC },
        temperatureMaxC = slice.maxOf { it.temperatureC },
        feelsLikeMinC = slice.minOf { it.feelsLikeC },
        feelsLikeMaxC = slice.maxOf { it.feelsLikeC },
        precipitationProbabilityMaxPct = slice.maxOf { it.precipitationProbabilityPct },
        hourly = slice,
    )
}

/** The day window the summary is *about*: [DAY_START_HOUR, DAY_END_HOUR). */
fun DailyForecast.daytime(): DailyForecast =
    window(LocalTime.of(DAY_START_HOUR, 0), LocalTime.of(DAY_END_HOUR, 0))

/**
 * The evening slice of *today*: [DAY_END_HOUR, end-of-day). Tomorrow's small hours
 * (00:00–07:00) aren't in scope yet — the bundle only carries today + yesterday —
 * so a late event ending after midnight is evaluated against the temps we have
 * for tonight, which is fine in practice.
 */
fun DailyForecast.evening(): DailyForecast =
    window(LocalTime.of(DAY_END_HOUR, 0), LocalTime.MAX)
