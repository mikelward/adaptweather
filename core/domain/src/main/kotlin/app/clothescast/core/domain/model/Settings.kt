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
    EN_CA("en-CA"),
    DE_DE("de-DE"),
    DE_AT("de-AT"),
    DE_CH("de-CH"),
    FR_FR("fr-FR"),
    FR_CA("fr-CA"),
    IT_IT("it-IT"),
    ES_ES("es-ES"),
    ES_MX("es-MX"),
    CA_ES("ca-ES"),
    RU_RU("ru-RU"),
    PL_PL("pl-PL"),
    HR_HR("hr-HR"),
    SL_SI("sl-SI"),
    SR_RS("sr-Latn-RS"),
    SR_CYRL_RS("sr-Cyrl-RS"),
    BG_BG("bg-BG"),
    CS_CZ("cs-CZ"),
    SK_SK("sk-SK"),
    HU_HU("hu-HU"),
    RO_RO("ro-RO"),
    EL_GR("el-GR"),
    UK_UA("uk-UA"),
    PT_BR("pt-BR"),
    PT_PT("pt-PT"),
    NL_NL("nl-NL"),
    SV_SE("sv-SE"),
    DA_DK("da-DK"),
    NB_NO("nb-NO"),
    FI_FI("fi-FI"),
    ET_EE("et-EE"),
    LV_LV("lv-LV"),
    LT_LT("lt-LT"),
    TR_TR("tr-TR"),
    EN_ZA("en-ZA"),
    ID_ID("id-ID"),
    MS_MY("ms-MY"),
    FIL_PH("fil-PH"),
    VI_VN("vi-VN"),
    TH_TH("th-TH"),
    ZH_CN("zh-CN"),
    ZH_TW("zh-TW"),
    HI_IN("hi-IN"),
    BN_BD("bn-BD"),
    JA_JP("ja-JP"),
    KO_KR("ko-KR"),
    AR_SA("ar-SA"),
    AR_EG("ar-EG"),
    AR_AE("ar-AE"),
    AR_MA("ar-MA"),
    HE_IL("he-IL"),
    FA_IR("fa-IR"),
    UR_PK("ur-PK"),
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
    EN_CA("en-CA"),
    DE_DE("de-DE"),
    DE_AT("de-AT"),
    DE_CH("de-CH"),
    FR_FR("fr-FR"),
    FR_CA("fr-CA"),
    IT_IT("it-IT"),
    ES_ES("es-ES"),
    ES_MX("es-MX"),
    CA_ES("ca-ES"),
    RU_RU("ru-RU"),
    PL_PL("pl-PL"),
    HR_HR("hr-HR"),
    SL_SI("sl-SI"),
    SR_RS("sr-Latn-RS"),
    SR_CYRL_RS("sr-Cyrl-RS"),
    BG_BG("bg-BG"),
    CS_CZ("cs-CZ"),
    SK_SK("sk-SK"),
    HU_HU("hu-HU"),
    RO_RO("ro-RO"),
    EL_GR("el-GR"),
    UK_UA("uk-UA"),
    PT_BR("pt-BR"),
    PT_PT("pt-PT"),
    NL_NL("nl-NL"),
    SV_SE("sv-SE"),
    DA_DK("da-DK"),
    NB_NO("nb-NO"),
    FI_FI("fi-FI"),
    ET_EE("et-EE"),
    LV_LV("lv-LV"),
    LT_LT("lt-LT"),
    TR_TR("tr-TR"),
    EN_ZA("en-ZA"),
    ID_ID("id-ID"),
    MS_MY("ms-MY"),
    FIL_PH("fil-PH"),
    VI_VN("vi-VN"),
    TH_TH("th-TH"),
    ZH_CN("zh-CN"),
    ZH_TW("zh-TW"),
    HI_IN("hi-IN"),
    BN_BD("bn-BD"),
    JA_JP("ja-JP"),
    KO_KR("ko-KR"),
    AR_SA("ar-SA"),
    HE_IL("he-IL"),
    FA_IR("fa-IR"),
    UR_PK("ur-PK"),
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
     * Per-clip OpenAI playback rate (multiplier). Mirrors the ElevenLabs speed
     * knob for consistency — same 0.7–1.2 range, same UI affordance. Sent as
     * the `speed` field on the speech request. The active model
     * (`gpt-4o-mini-tts`) currently steers pace primarily through the
     * `instructions` field, but the wire parameter is harmless when ignored
     * and forward-compatible with `tts-1` / `tts-1-hd`. Only consulted when
     * [ttsEngine] == [TtsEngine.OPENAI].
     */
    val openAiSpeed: Double = DEFAULT_OPENAI_SPEED,
    /**
     * ElevenLabs voice ID — the opaque library identifier (e.g.
     * "EXAVITQu4vr4xnSDxMaL" for Sarah). Only consulted when [ttsEngine]
     * == [TtsEngine.ELEVENLABS].
     */
    val elevenLabsVoice: String = DEFAULT_ELEVENLABS_VOICE,
    /**
     * ElevenLabs synthesis model ID (e.g. `eleven_turbo_v2_5`,
     * `eleven_multilingual_v2`, `eleven_flash_v2_5`, `eleven_v3`). Stored
     * as a free-form string so adding a new model doesn't need a domain
     * enum migration. Only consulted when [ttsEngine] ==
     * [TtsEngine.ELEVENLABS].
     */
    val elevenLabsModel: String = DEFAULT_ELEVENLABS_MODEL,
    /**
     * Per-clip ElevenLabs playback rate (multiplier, 0.7–1.2 per the
     * documented range). Default 1.0 (vendor stock pace). Only consulted
     * when [ttsEngine] == [TtsEngine.ELEVENLABS].
     */
    val elevenLabsSpeed: Double = DEFAULT_ELEVENLABS_SPEED,
    /**
     * Per-clip ElevenLabs `voice_settings.stability` (0.0–1.0). Higher =
     * steadier pronunciation, lower = more expressive. Default 0.65 favours
     * pronunciation consistency over expression for short weather briefings.
     * Only consulted when [ttsEngine] == [TtsEngine.ELEVENLABS].
     */
    val elevenLabsStability: Double = DEFAULT_ELEVENLABS_STABILITY,
    /**
     * On-device TextToSpeech voice ID (e.g. "en-us-x-tpc-network"). Only
     * consulted when [ttsEngine] == [TtsEngine.DEVICE]. `null` (the default)
     * means "auto-pick the highest-quality voice for [voiceLocale]" — the
     * existing behaviour for installs that predate the device-voice picker.
     * Stored as a free-form string so the device's installed-voice catalogue
     * doesn't have to round-trip through a domain enum.
     */
    val deviceVoice: String? = null,
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
    /**
     * Delivery mode for the evening / "tonight" insight. Distinct from
     * [deliveryMode] (the morning slot) so the user can keep the morning as a
     * silent notification and have the evening read itself out, or vice versa.
     * The repository falls the stored tonight value back to [deliveryMode] when
     * absent so existing installs keep their old "shared mode" behaviour until
     * the user explicitly diverges them.
     */
    val tonightDeliveryMode: DeliveryMode = deliveryMode,
    /**
     * When true, the nightly insight only posts a notification (and only speaks
     * via TTS) on evenings with calendar events; on event-free evenings it
     * still refreshes silently — caches the insight and updates the widget /
     * Today card — but skips the notification entirely. When false (default),
     * the silent notification channel still posts a no-sound notification on
     * empty evenings, which is what the existing tonight notifier did.
     */
    val tonightNotifyOnlyOnEvents: Boolean = false,
    /**
     * When true, the morning insight tacks on a brief mention of any evening
     * calendar events with a clothing tip keyed to the *evening* forecast — e.g.
     * "Bring a jacket for your 9pm dinner." The tip is gated on
     * [useCalendarEvents] (no events without that), and only fires when at least
     * one clothes rule triggers against the evening hourly slice. Off by default.
     */
    val dailyMentionEveningEvents: Boolean = false,
    /**
     * Feels-like cutoffs that decide which top + bottom the home screen renders
     * as the glanceable outfit. Defaults match the previously-hardcoded values
     * in [OutfitSuggestion.fromForecast] so a fresh install picks the same
     * outfit as before. Adjusted from the rationale dialog's `−1°` / `+1°`
     * controls when the user pushes back on a recommendation.
     */
    val outfitThresholds: OutfitSuggestion.Thresholds = OutfitSuggestion.Thresholds.DEFAULT,
) {
    companion object {
        const val DEFAULT_GEMINI_VOICE = "Kore"
        const val DEFAULT_OPENAI_VOICE = "alloy"
        // Sarah — the most generally pleasant of ElevenLabs's stock library voices.
        const val DEFAULT_ELEVENLABS_VOICE = "EXAVITQu4vr4xnSDxMaL"
        // Mirrors `core:data:ElevenLabsTtsClient.DEFAULT_ELEVENLABS_TTS_MODEL`
        // — duplicated as a literal so domain stays Android- and data-layer-
        // independent. Update both sides together if the default ever shifts.
        const val DEFAULT_ELEVENLABS_MODEL = "eleven_turbo_v2_5"
        // Playback-rate multiplier applied per request (clamped 0.7–1.2 by
        // the picker UI; ElevenLabs documents the same range). Default 1.0
        // matches the vendor's stock pace.
        const val DEFAULT_ELEVENLABS_SPEED = 1.0
        const val MIN_ELEVENLABS_SPEED = 0.7
        const val MAX_ELEVENLABS_SPEED = 1.2
        // Documented voice_settings.stability range is 0–1. Default mirrors
        // the previously-hardcoded value in ElevenLabsTtsClient so existing
        // installs hear no change after the slider lands.
        const val DEFAULT_ELEVENLABS_STABILITY = 0.65
        const val MIN_ELEVENLABS_STABILITY = 0.0
        const val MAX_ELEVENLABS_STABILITY = 1.0

        // Same range as ElevenLabs speed for UI parity. OpenAI's API
        // accepts 0.25–4.0 but values outside ~0.7–1.2 sound robotic / silly
        // for a weather briefing; matching the ElevenLabs slider keeps the
        // UX consistent and prevents users from accidentally picking 4×.
        // Default 1.0 because there are no field reports of OpenAI sounding
        // "too fast" the way ElevenLabs has.
        const val DEFAULT_OPENAI_SPEED = 1.0
        const val MIN_OPENAI_SPEED = 0.7
        const val MAX_OPENAI_SPEED = 1.2
    }
}
