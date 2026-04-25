package com.adaptweather.core.data.location

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
)

@Serializable
internal data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)
