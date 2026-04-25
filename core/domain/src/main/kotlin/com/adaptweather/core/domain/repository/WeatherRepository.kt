package com.adaptweather.core.domain.repository

import com.adaptweather.core.domain.model.ConfidenceInfo
import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.WeatherAlert

/**
 * Source of weather data. Implementations are responsible for combining yesterday's
 * actual weather with today's forecast — Open-Meteo's `forecast?past_days=1` returns
 * both in one response, so a single call satisfies this contract.
 *
 * Severe-weather alerts are returned alongside the forecast in [ForecastBundle.alerts].
 * Implementations should treat the alerts feed as best-effort: a failure to fetch
 * warnings must not fail the whole forecast call — return an empty list instead.
 *
 * Cross-model confidence ([ForecastBundle.confidence]) is also best-effort: implementations
 * may return null when the multi-model fetch fails or isn't supported.
 */
interface WeatherRepository {
    suspend fun fetchForecast(location: Location): ForecastBundle
}

data class ForecastBundle(
    val today: DailyForecast,
    val yesterday: DailyForecast,
    val alerts: List<WeatherAlert> = emptyList(),
    val confidence: ConfidenceInfo? = null,
) {
    init {
        require(yesterday.date.isBefore(today.date)) {
            "yesterday (${yesterday.date}) must be before today (${today.date})"
        }
    }
}
