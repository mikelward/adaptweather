package app.clothescast.core.data.location

import app.clothescast.core.domain.model.Location
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.http.path
import java.util.logging.Logger

internal const val GEOCODING_HOST = "geocoding-api.open-meteo.com"

/**
 * Resolves a free-text place name to one or more candidate [Location]s. Free, key-less
 * Open-Meteo service, separate host from the forecast API.
 *
 * Returns at most [limit] candidates ordered by Open-Meteo's relevance ranking. The
 * displayName is built as "<name>, <admin1>, <country>" with missing parts elided so
 * a city like "London" surfaces as "London, England, United Kingdom" while a less
 * granular match shows just what's available.
 */
class OpenMeteoGeocodingClient(private val httpClient: HttpClient) {
    suspend fun search(query: String, limit: Int = 5, languageTag: String = "en"): List<Location> {
        if (query.isBlank()) return emptyList()

        val response: OpenMeteoGeocodingResponse = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = GEOCODING_HOST
                path("v1", "search")
            }
            parameter("name", query.trim())
            parameter("count", limit)
            parameter("language", languageTag)
            parameter("format", "json")
        }.body()

        response.results.orEmpty().forEachIndexed { i, r ->
            logger.fine("geocoding result[$i]: " +
                "id=${r.id} name=${r.name} " +
                "lat=${r.latitude} lon=${r.longitude} elevation=${r.elevation} " +
                "featureCode=${r.featureCode} " +
                "countryCode=${r.countryCode} countryId=${r.countryId} country=${r.country} " +
                "timezone=${r.timezone} population=${r.population} " +
                "admin1Id=${r.admin1Id} admin1=${r.admin1} " +
                "admin2Id=${r.admin2Id} admin2=${r.admin2} " +
                "admin3Id=${r.admin3Id} admin3=${r.admin3} " +
                "admin4Id=${r.admin4Id} admin4=${r.admin4}")
        }
        return response.results.orEmpty().map { it.toDomain() }
    }

    private fun GeocodingResult.toDomain(): Location {
        val displayName = listOfNotNull(name, admin1, country).joinToString(", ")
        return Location(latitude = latitude, longitude = longitude, displayName = displayName)
    }

    private companion object {
        val logger: Logger = Logger.getLogger(OpenMeteoGeocodingClient::class.java.name)
    }
}
