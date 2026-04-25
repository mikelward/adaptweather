package com.adaptweather.core.data.weather

import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.WeatherAlert
import com.adaptweather.core.domain.repository.ForecastBundle
import com.adaptweather.core.domain.repository.WeatherRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException

internal const val OPEN_METEO_HOST = "api.open-meteo.com"

/**
 * Open-Meteo `forecast` endpoint with past_days=1&forecast_days=1, returning yesterday's
 * actuals plus today's forecast in one call. Free, key-less. Free-tier soft cap is 10k
 * requests/day; this app makes one call per device per day.
 *
 * Severe-weather alerts come from a second `/v1/warnings` call. The warnings endpoint
 * is best-effort: if it fails (4xx, 5xx, network), we return the forecast with an empty
 * alerts list rather than failing the whole fetch — a missing alerts feed must not
 * suppress the daily insight.
 */
class OpenMeteoClient(private val httpClient: HttpClient) : WeatherRepository {
    override suspend fun fetchForecast(location: Location): ForecastBundle {
        val response: OpenMeteoResponse = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = OPEN_METEO_HOST
                path("v1", "forecast")
            }
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("past_days", 1)
            parameter("forecast_days", 1)
            parameter("timezone", "auto")
            parameter(
                "daily",
                "temperature_2m_min,temperature_2m_max,apparent_temperature_min,apparent_temperature_max," +
                    "precipitation_probability_max,precipitation_sum,weather_code",
            )
            parameter(
                "hourly",
                "temperature_2m,apparent_temperature,precipitation_probability,weather_code",
            )
        }.body()

        val bundle = OpenMeteoMapper.toBundle(response)
        return bundle.copy(alerts = fetchAlerts(location))
    }

    private suspend fun fetchAlerts(location: Location): List<WeatherAlert> = try {
        val response: OpenMeteoWarningsResponse = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = OPEN_METEO_HOST
                path("v1", "warnings")
            }
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("timezone", "auto")
        }.body()
        OpenMeteoWarningsMapper.toAlerts(response)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        emptyList()
    }
}
