package app.clothescast.core.domain.model

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
 *   ElevenLabs key; falls back to [DEVICE] on failure.
 */
enum class TtsEngine { DEVICE, GEMINI, OPENAI, ELEVENLABS }

/**
 * User-selectable accent / language preference for spoken playback. Used by
 * each engine as best fits its capabilities:
 *
 *  - [TtsEngine.DEVICE] picks the actual voice to speak in (the Android
 *    TextToSpeech engine has separate en-US / en-GB / en-AU voices).
 *  - [TtsEngine.GEMINI] passes the locale through as a natural-language
 *    accent directive prepended to the prompt — Gemini's prebuilt voices are
 *    language-agnostic personalities and follow that direction.
 *  - [TtsEngine.OPENAI] and [TtsEngine.ELEVENLABS] both filter the voice
 *    picker to voices whose baked-in accent matches the variant, falling
 *    back to the full list if no voice matches. Their voices have fixed
 *    accents that prompt-steering can't reliably override (we tried; only
 *    voice selection actually changes the audible accent), so the variant
 *    has to drive *which voice* gets used rather than *how* a given voice
 *    speaks. For OpenAI, [defaultOpenAiVoiceFor] also picks a sensible
 *    starting voice for first-launch users (en-GB → `fable`, else `nova`)
 *    so the user hears the right accent before they ever open Settings.
 *
 * [SYSTEM] means "follow the phone's locale" — the right default for almost
 * everyone, since their device language already encodes their accent
 * preference. The explicit en-* options are for users whose phone locale
 * doesn't match the accent they want to hear (e.g. an en-AU speaker on an
 * en-US phone).
 */
enum class VoiceLocale(val bcp47: String?) {
    SYSTEM(null),
    EN_US("en-US"),
    EN_GB("en-GB"),
    EN_AU("en-AU"),
    DE_DE("de-DE"),
}

/**
 * The user's region — drives the language used for rendered insight text and
 * the *default* unit choices for users who haven't explicitly picked units yet.
 *
 * [SYSTEM] (the default) means "follow the phone's locale": the right answer
 * for almost everyone, since their device language already encodes where they
 * are. The explicit en-* options are for users on a phone whose locale doesn't
 * match the region they want the app to behave as (e.g. an en-AU traveller on
 * an en-US phone).
 *
 * Distinct from [VoiceLocale]: that one is specifically about the *spoken
 * accent* of the audio playback — you might be in Australia but prefer a US
 * voice, or vice versa. Region is the higher-level "where am I" setting.
 */
enum class Region(val bcp47: String?) {
    SYSTEM(null),
    EN_US("en-US"),
    EN_GB("en-GB"),
    EN_AU("en-AU"),
    DE_DE("de-DE"),
}

data class UserPreferences(
    val schedule: Schedule,
    val deliveryMode: DeliveryMode,
    val region: Region = Region.SYSTEM,
    val temperatureUnit: TemperatureUnit,
    val distanceUnit: DistanceUnit,
    val clothesRules: List<ClothesRule>,
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
    val voiceLocale: VoiceLocale = VoiceLocale.SYSTEM,
    /**
     * When true, the worker reads today's calendar events (via `READ_CALENDAR`)
     * and feeds them into the insight summary so the rendered string can tie a
     * clothes suggestion to a specific event ("bring an umbrella for your 3pm
     * standup"). Off by default — the user must both enable the toggle and grant
     * the runtime permission for events to actually be read.
     */
    val useCalendarEvents: Boolean = false,
    /**
     * When the evening / "tonight" insight should fire. Distinct from [schedule]
     * (the morning slot) so the user can keep the morning at 07:00 and still
     * tweak the evening time independently. Default is 19:00 every day.
     */
    val tonightSchedule: Schedule = Schedule.defaultTonight(schedule.zoneId),
    /**
     * Master switch for the evening / "tonight" insight. On by default — the
     * tonight notifier is silent when there are no calendar events for the
     * evening, so it's not noisy out of the box. The user can disable it from
     * the schedule settings page.
     */
    val tonightEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_GEMINI_VOICE = "Kore"
        const val DEFAULT_OPENAI_VOICE = "alloy"
        // Sarah — the most generally pleasant of ElevenLabs's stock library voices.
        const val DEFAULT_ELEVENLABS_VOICE = "EXAVITQu4vr4xnSDxMaL"
    }
}
