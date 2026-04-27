package app.clothescast.core.data.weather

import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.repository.ForecastBundle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Maps an [OpenMeteoResponse] (queried with past_days=1&forecast_days=2) into a
 * [ForecastBundle]. Index 0 in the daily arrays is yesterday, index 1 is today,
 * index 2 (when present) is tomorrow. The daily fields for tomorrow are not surfaced
 * — only its hourly entries — but the third daily entry is what makes Open-Meteo
 * return tomorrow's hours at all.
 *
 * The hourly stream covers all three days. We split it by date: today's hours
 * attach to the today forecast, tomorrow's hours flow through on
 * [ForecastBundle.tomorrowHourly] so the tonight insight can wrap from 19:00 today
 * into tomorrow's pre-dawn morning. Yesterday's hourlies are not needed downstream.
 *
 * Tolerates 2-entry responses (forecast_days=1) for backwards compatibility with
 * older fixtures and any caller that downgrades the request — tomorrow's hourly
 * just comes through empty in that case.
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

        val tomorrowHourly = if (response.daily.time.size >= 3) {
            val tomorrowDate = LocalDate.parse(response.daily.time[2])
            response.hourly.forDate(tomorrowDate)
        } else {
            emptyList()
        }

        return ForecastBundle(today = today, yesterday = yesterday, tomorrowHourly = tomorrowHourly)
    }

    private fun DailyData.toForecast(index: Int, date: LocalDate, hourly: List<HourlyForecast>): DailyForecast {
        val rawMin = temperatureMin[index] ?: 0.0
        val rawMax = temperatureMax[index] ?: 0.0
        return DailyForecast(
            date = date,
            temperatureMinC = rawMin,
            temperatureMaxC = rawMax,
            // Fall back to raw temp if Open-Meteo didn't provide apparent — better than 0 °C.
            feelsLikeMinC = feelsLikeMin[index] ?: rawMin,
            feelsLikeMaxC = feelsLikeMax[index] ?: rawMax,
            precipitationProbabilityMaxPct = (precipitationProbabilityMax[index] ?: 0).toDouble(),
            precipitationMmTotal = precipitationSum[index] ?: 0.0,
            condition = WmoCodeMapper.map(weatherCode[index]),
            hourly = hourly,
        )
    }

    private fun HourlyData.forDate(date: LocalDate): List<HourlyForecast> {
        val out = ArrayList<HourlyForecast>(24)
        for (i in time.indices) {
            val ts = LocalDateTime.parse(time[i])
            if (ts.toLocalDate() != date) continue
            val raw = temperature[i] ?: 0.0
            out += HourlyForecast(
                time = LocalTime.of(ts.hour, ts.minute),
                temperatureC = raw,
                feelsLikeC = feelsLike[i] ?: raw,
                precipitationProbabilityPct = (precipitationProbability[i] ?: 0).toDouble(),
                condition = WmoCodeMapper.map(weatherCode[i]),
            )
        }
        return out
    }
}
