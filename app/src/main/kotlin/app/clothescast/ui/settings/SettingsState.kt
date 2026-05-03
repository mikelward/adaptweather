package app.clothescast.ui.settings

import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.ThemeMode
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.data.defaultDistanceUnitFor
import app.clothescast.data.defaultTemperatureUnitFor
import app.clothescast.tts.DeviceVoice
import app.clothescast.tts.TtsVoiceOption
import app.clothescast.tts.defaultOpenAiVoiceFor
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

/** What [SettingsScreen] needs to render. */
data class SettingsState(
    val scheduleTime: LocalTime = LocalTime.of(7, 0),
    val scheduleDays: Set<DayOfWeek> = Schedule.EVERY_DAY,
    val tonightTime: LocalTime = LocalTime.of(19, 0),
    val tonightDays: Set<DayOfWeek> = Schedule.EVERY_DAY,
    val tonightEnabled: Boolean = true,
    val tonightNotifyOnlyOnEvents: Boolean = false,
    val deliveryMode: DeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
    val tonightDeliveryMode: DeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
    val dailyMentionEveningEvents: Boolean = false,
    val region: Region = Region.SYSTEM,
    // Match SettingsRepository's locale-aware defaults so en-US devices don't
    // briefly render °C / km before the first DataStore emission overrides it.
    // Region.SYSTEM falls through to the phone locale, mirroring the repository.
    val temperatureUnit: TemperatureUnit = defaultTemperatureUnitFor(Locale.getDefault()),
    val distanceUnit: DistanceUnit = defaultDistanceUnitFor(Locale.getDefault()),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clothesRules: List<ClothesRule> = ClothesRule.DEFAULTS,
    val location: Location? = null,
    val useDeviceLocation: Boolean = false,
    val ttsEngine: TtsEngine = TtsEngine.DEVICE,
    val geminiVoice: String = UserPreferences.DEFAULT_GEMINI_VOICE,
    // Match SettingsRepository's locale-aware default so the picker doesn't
    // briefly render "alloy" before the first DataStore emission overrides it.
    // Region isn't known yet at construction time, so this initial value falls
    // back to the phone's current locale → fable on en-GB, nova everywhere else.
    // The DataStore emission immediately corrects it using the stored region.
    val openAiVoice: String = defaultOpenAiVoiceFor(VoiceLocale.SYSTEM),
    val openAiSpeed: Double = UserPreferences.DEFAULT_OPENAI_SPEED,
    val elevenLabsVoice: String = UserPreferences.DEFAULT_ELEVENLABS_VOICE,
    val elevenLabsModel: String = UserPreferences.DEFAULT_ELEVENLABS_MODEL,
    val elevenLabsSpeed: Double = UserPreferences.DEFAULT_ELEVENLABS_SPEED,
    val elevenLabsStability: Double = UserPreferences.DEFAULT_ELEVENLABS_STABILITY,
    /**
     * On-device voice ID the user has pinned, or `null` for "auto-pick the
     * highest-quality voice for [voiceLocale]" (the default for installs
     * that haven't opened the device-voice picker).
     */
    val deviceVoice: String? = null,
    /**
     * Voices the device's TTS engine reports for the current [voiceLocale],
     * loaded eagerly by [SettingsViewModel] on first preferences emission
     * and refreshed on every locale change — *not* gated on DEVICE being
     * the currently-selected engine. Pre-loading means switching to DEVICE
     * doesn't briefly show "loading…", and the cost is one engine bind per
     * locale change, which is rare. Empty until enumeration completes —
     * the picker shows the pinned ID alone in that window, so users still
     * see what they previously selected.
     */
    val deviceVoices: List<DeviceVoice> = emptyList(),
    /**
     * What [app.clothescast.tts.AndroidTtsSpeaker] would speak right now —
     * the user's [deviceVoice] resolved against the engine's catalogue, or
     * the auto-pick if no pin. `null` while the enumeration is in flight or
     * if the engine reports no voices at all.
     */
    val effectiveDeviceVoice: DeviceVoice? = null,
    val voiceLocale: VoiceLocale = VoiceLocale.SYSTEM,
    val useCalendarEvents: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val openAiKeyConfigured: Boolean = false,
    val elevenLabsKeyConfigured: Boolean = false,
    /**
     * Voices fetched from `GET /v1/voices` after the user taps Refresh in
     * Settings. `null` means "no refresh has happened this session" — the
     * picker falls back to the curated [app.clothescast.tts.ELEVENLABS_VOICES]
     * list. An empty list means the API returned no voices (exotic
     * account state) and is rendered the same way as the fallback so the
     * picker is never empty.
     */
    val elevenLabsRefreshedVoices: List<TtsVoiceOption>? = null,
    val elevenLabsRefreshing: Boolean = false,
    /**
     * True while the WorkManager location-cache-refresh job is ENQUEUED,
     * RUNNING, or BLOCKED. Drives "Detecting…" in the Location settings
     * card — the label only shows "Detecting…" while this is true, so it
     * can't get stuck after the worker finishes without resolving a fix.
     */
    val locationDetecting: Boolean = false,
)
