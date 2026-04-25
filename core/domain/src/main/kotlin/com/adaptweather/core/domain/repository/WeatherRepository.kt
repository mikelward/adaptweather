package com.adaptweather.core.domain.repository

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.Location

/**
 * Source of weather data. Implementations are responsible for combining yesterday's
 * actual weather with today's forecast — Open-Meteo's `forecast?past_days=1` returns
 * both in one response, so a single call satisfies this contract.
 */
interface WeatherRepository {
    suspend fun fetchForecast(location: Location): ForecastBundle
}

data class ForecastBundle(
    val today: DailyForecast,
    val yesterday: DailyForecast,
) {
    init {
        require(yesterday.date.isBefore(today.date)) {
            "yesterday (${yesterday.date}) must be before today (${today.date})"
        }
    }
}
