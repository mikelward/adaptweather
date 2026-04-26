package app.adaptweather.core.data.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire type for `POST /v1/text-to-speech/{voice_id}`. Field names match
 * ElevenLabs's snake_case via [SerialName]. Request only — the response is
 * raw audio bytes (no JSON to deserialize), so there's no response DTO.
 */
@Serializable
internal data class ElevenLabsSpeechRequest(
    val text: String,
    @SerialName("model_id")
    val modelId: String,
)
