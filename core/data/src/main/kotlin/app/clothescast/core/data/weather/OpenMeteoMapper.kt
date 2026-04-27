package app.clothescast.core.data.weather

import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.repository.ForecastBundle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Maps an [OpenMeteoResponse] (queried with past_days=1&forecast_days=2) into a
 * [ForecastBundle]. Index 0 in the daily arrays is yesterday, index 1 is today;
 * tomorrow (index 2) is fetched only so the hourly stream reliably reaches end of
 * today, and is otherwise discarded. The hourly stream covers all three days; we
 * filter to today's date for the today forecast (yesterday's and tomorrow's
 * hourlies are not needed downstream).
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
