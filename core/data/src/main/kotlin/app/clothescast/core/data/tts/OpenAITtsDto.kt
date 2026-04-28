package app.clothescast.core.data.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire type for `POST /v1/audio/speech`. Fields whose Kotlin name doesn't
 * match OpenAI's snake_case (e.g. `responseFormat` ↔ `response_format`)
 * carry [SerialName]; fields where the names already line up (`model`,
 * `input`, `voice`, `instructions`) don't need it. Request only — the
 * response is raw audio bytes (no JSON to deserialize), so there's no
 * response DTO.
 */
@Serializable
internal data class OpenAISpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format")
    val responseFormat: String,
    /**
     * Free-form steering for non-accent attributes (pace, clarity, emotion).
     * Earlier field-testing showed this *cannot* override the voice's
     * baked-in accent — that part stays voice-bound — but pace and
     * enunciation directives do land. Null when we have nothing to add.
     */
    val instructions: String? = null,
)
