package app.adaptweather.core.domain.model

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
