package com.adaptweather.core.data.weather

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.HourlyForecast
import com.adaptweather.core.domain.repository.ForecastBundle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Maps an [OpenMeteoResponse] (queried with past_days=1&forecast_days=1) into a
 * [ForecastBundle]. Index 0 in the daily arrays is yesterday, index 1 is today.
 * The hourly stream covers both days; we only attach today's hours to the today
 * forecast (yesterday's hourlies are not needed by the prompt).
 */
internal object OpenMeteoMapper {
    fun toBundle(response: OpenMeteoResponse): ForecastBundle {
        require(response.daily.time.size >= 2) {
            "Open-Meteo response must include 2 daily entries (yesterday + today); got ${response.daily.time.size}"
        }

        val yesterdayDate = LocalDate.parse(response.daily.time[0])
        val todayDate = LocalDate.parse(response.daily.time[1])

        val yesterday = response.daily.toForecast(index = 0, date = yesterdayDate, hourly = emptyList())
        val todayHourly = response.hourly.forDate(todayDate)
        val today = response.daily.toForecast(index = 1, date = todayDate, hourly = todayHourly)

        return ForecastBundle(today = today, yesterday = yesterday)
    }

    private fun DailyData.toForecast(index: Int, date: LocalDate, hourly: List<HourlyForecast>): DailyForecast =
        DailyForecast(
            date = date,
            temperatureMinC = temperatureMin[index] ?: 0.0,
            temperatureMaxC = temperatureMax[index] ?: 0.0,
            precipitationProbabilityMaxPct = (precipitationProbabilityMax[index] ?: 0).toDouble(),
            precipitationMmTotal = precipitationSum[index] ?: 0.0,
            condition = WmoCodeMapper.map(weatherCode[index]),
            hourly = hourly,
        )

    private fun HourlyData.forDate(date: LocalDate): List<HourlyForecast> {
        val out = ArrayList<HourlyForecast>(24)
        for (i in time.indices) {
            val ts = LocalDateTime.parse(time[i])
            if (ts.toLocalDate() != date) continue
            out += HourlyForecast(
                time = LocalTime.of(ts.hour, ts.minute),
                temperatureC = temperature[i] ?: 0.0,
                precipitationProbabilityPct = (precipitationProbability[i] ?: 0).toDouble(),
                condition = WmoCodeMapper.map(weatherCode[i]),
            )
        }
        return out
    }
}
