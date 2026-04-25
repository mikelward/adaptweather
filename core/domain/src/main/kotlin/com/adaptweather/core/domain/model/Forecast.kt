package com.adaptweather.core.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class DailyForecast(
    val date: LocalDate,
    val temperatureMinC: Double,
    val temperatureMaxC: Double,
    val precipitationProbabilityMaxPct: Double,
    val precipitationMmTotal: Double,
    val condition: WeatherCondition,
    val hourly: List<HourlyForecast> = emptyList(),
)

data class HourlyForecast(
    val time: LocalTime,
    val temperatureC: Double,
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
