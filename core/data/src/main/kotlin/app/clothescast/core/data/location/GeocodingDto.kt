package app.clothescast.core.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for `geocoding-api.open-meteo.com/v1/search`. Same key-less, free service
 * Open-Meteo provides for forecasts. Results may be empty when the query doesn't match
 * any known place.
 */
@Serializable
internal data class OpenMeteoGeocodingResponse(
    val results: List<GeocodingResult>? = null,
    @SerialName("generationtime_ms") val generationtimeMs: Double? = null,
)

@Serializable
internal data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("feature_code") val featureCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_id") val countryId: Long? = null,
    val country: String? = null,
    val timezone: String? = null,
    val population: Long? = null,
    @SerialName("admin1_id") val admin1Id: Long? = null,
    @SerialName("admin2_id") val admin2Id: Long? = null,
    @SerialName("admin3_id") val admin3Id: Long? = null,
    @SerialName("admin4_id") val admin4Id: Long? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val admin3: String? = null,
    val admin4: String? = null,
)
