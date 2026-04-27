package app.clothescast.core.data.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire type for `POST /v1/audio/speech`. Field names match OpenAI's snake_case
 * via [SerialName]. Request only — the response is raw audio bytes (no JSON to
 * deserialize), so there's no response DTO.
 *
 * [instructions] is only honoured by `gpt-4o-mini-tts` (silently ignored by
 * `tts-1` / `tts-1-hd`). Stays null when the caller has no accent / style
 * direction; kotlinx's default `encodeDefaults = false` keeps the field off
 * the wire in that case.
 */
@Serializable
internal data class OpenAISpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format")
    val responseFormat: String,
    val instructions: String? = null,
)
