package app.adaptweather.core.data.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire type for `POST /v1/audio/speech`. Field names match OpenAI's snake_case
 * via [SerialName]. Request only — the response is raw audio bytes (no JSON to
 * deserialize), so there's no response DTO.
 */
@Serializable
internal data class OpenAISpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format")
    val responseFormat: String,
)
