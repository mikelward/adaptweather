package app.adaptweather.core.domain.model

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class DistanceUnit { KILOMETERS, MILES }

enum class DeliveryMode { NOTIFICATION_ONLY, TTS_ONLY, NOTIFICATION_AND_TTS }

/**
 * Where the spoken-aloud audio comes from.
 *
 * - [DEVICE] uses Android's on-device TextToSpeech engine. Free, fully offline once
 *   voices are installed, but quality varies by vendor.
 * - [GEMINI] uses Gemini's audio-output model (`gemini-2.5-flash-preview-tts`) over
 *   the BYOK Gemini key. Near-human quality, requires network at speak-time, costs
 *   a small amount per character. Falls back to [DEVICE] if the call fails.
 * - [OPENAI] uses OpenAI's `audio/speech` endpoint over a separate BYOK OpenAI key.
 *   Comparable quality to Gemini; falls back to [DEVICE] on failure.
 * - [ELEVENLABS] uses ElevenLabs's `text-to-speech` endpoint over a separate BYOK
 *   ElevenLabs key. Highest perceptual naturalness of the three online options;
 *   falls back to [DEVICE] on failure.
 */
enum class TtsEngine { DEVICE, GEMINI, OPENAI, ELEVENLABS }

data class UserPreferences(
    val schedule: Schedule,
    val deliveryMode: DeliveryMode,
    val temperatureUnit: TemperatureUnit,
    val distanceUnit: DistanceUnit,
    val wardrobeRules: List<WardrobeRule>,
    /**
     * The fixed location to fetch weather for when [useDeviceLocation] is false (or as a
     * fallback when device location can't be resolved). Null when the user has not
     * configured one.
     */
    val location: Location? = null,
    /**
     * When true, the worker tries to read the device's coarse location at notify time
     * (network provider — no GPS hardware fix needed) and falls back to [location] /
     * platform default if the read fails or permission is not granted.
     */
    val useDeviceLocation: Boolean = false,
    val ttsEngine: TtsEngine = TtsEngine.DEVICE,
    /**
     * Prebuilt Gemini voice name (e.g. "Kore", "Puck"). Only consulted when
     * [ttsEngine] == [TtsEngine.GEMINI]. Stored as a free-form string so adding
     * voices doesn't require a domain enum migration.
     */
    val geminiVoice: String = DEFAULT_GEMINI_VOICE,
    /**
     * OpenAI voice name (e.g. "alloy", "echo"). Only consulted when [ttsEngine]
     * == [TtsEngine.OPENAI].
     */
    val openAiVoice: String = DEFAULT_OPENAI_VOICE,
    /**
     * ElevenLabs voice ID — the opaque library identifier (e.g.
     * "EXAVITQu4vr4xnSDxMaL" for Sarah). Only consulted when [ttsEngine]
     * == [TtsEngine.ELEVENLABS].
     */
    val elevenLabsVoice: String = DEFAULT_ELEVENLABS_VOICE,
) {
    companion object {
        const val DEFAULT_GEMINI_VOICE = "Kore"
        const val DEFAULT_OPENAI_VOICE = "alloy"
        // Sarah — the most generally pleasant of ElevenLabs's stock library voices.
        const val DEFAULT_ELEVENLABS_VOICE = "EXAVITQu4vr4xnSDxMaL"
    }
}
