package app.clothescast.core.data.tts

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
    /**
     * Per-clip voice tuning. Sent for every request so our defaults override
     * each voice's library-stored defaults consistently — without this the
     * pacing varies by which voice the user picked, which makes the
     * "everything sounds rushed" problem worse.
     */
    @SerialName("voice_settings")
    val voiceSettings: ElevenLabsVoiceSettings? = null,
)

/**
 * The subset of `voice_settings` we actually send. Fields we leave null
 * fall back to the voice's stored library defaults — keep this list short
 * so we're not silently overriding things voice authors tuned.
 *
 * - [speed] (0.7–1.2, default 1.0): playback rate. We default below 1.0
 *   because field reports flagged the stock pace as "too fast" and dropping
 *   letters; a small slowdown buys clarity without sounding draggy.
 * - [stability] (0–1, default 0.5): higher = steadier pronunciation, lower
 *   = more expressive. For 1–2 sentence weather briefings, consistent
 *   pronunciation matters more than expression — bump from the stock 0.5.
 */
@Serializable
internal data class ElevenLabsVoiceSettings(
    val speed: Double? = null,
    val stability: Double? = null,
)

/**
 * Wire type for `GET /v1/voices`. The endpoint returns the full library
 * available to the caller's API key — premade voices, generated voices and
 * the user's clones — wrapped in a `voices` array. We only consume the
 * fields that drive the picker label; everything else (`samples`,
 * `fine_tuning`, `available_for_tiers`, …) is ignored via Json's
 * `ignoreUnknownKeys = true`.
 */
@Serializable
internal data class ElevenLabsVoicesResponse(
    val voices: List<ElevenLabsVoiceDto> = emptyList(),
)

@Serializable
internal data class ElevenLabsVoiceDto(
    @SerialName("voice_id")
    val voiceId: String,
    val name: String,
    val category: String? = null,
    val labels: Map<String, String>? = null,
)
