package app.clothescast.core.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Open-Meteo `/v1/warnings` endpoint. The feed is a list of
 * CAP-shaped alerts. All free-text fields are nullable — the upstream provider may
 * publish only an event type and a valid window.
 */
@Serializable
internal data class OpenMeteoWarningsResponse(
    @SerialName("warnings") val warnings: List<WarningDto> = emptyList(),
)

@Serializable
internal data class WarningDto(
    @SerialName("event") val event: String,
    @SerialName("severity") val severity: String? = null,
    @SerialName("headline") val headline: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("onset") val onset: String,
    @SerialName("expires") val expires: String,
)
