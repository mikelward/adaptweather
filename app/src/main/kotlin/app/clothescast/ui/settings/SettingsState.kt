package app.clothescast.ui.settings

import app.clothescast.core.domain.model.CastLength
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.data.defaultDistanceUnitFor
import app.clothescast.data.defaultTemperatureUnitFor
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
    val castLength: CastLength = CastLength.SHORTER,
    val region: Region = Region.SYSTEM,
    // Match SettingsRepository's locale-aware defaults so en-US devices don't
    // briefly render °C / km before the first DataStore emission overrides it.
    // Region.SYSTEM falls through to the phone locale, mirroring the repository.
    val temperatureUnit: TemperatureUnit = defaultTemperatureUnitFor(Locale.getDefault()),
    val distanceUnit: DistanceUnit = defaultDistanceUnitFor(Locale.getDefault()),
    val clothesRules: List<ClothesRule> = ClothesRule.DEFAULTS,
    val location: Location? = null,
    val useDeviceLocation: Boolean = false,
    val ttsEngine: TtsEngine = TtsEngine.DEVICE,
    val geminiVoice: String = UserPreferences.DEFAULT_GEMINI_VOICE,
    // Match SettingsRepository's locale-aware default so the picker doesn't
    // briefly render "alloy" before the first DataStore emission overrides it.
    // When voiceLocale is SYSTEM (the default), this resolves through the phone's
    // current locale → fable on en-GB, nova everywhere else.
    val openAiVoice: String = defaultOpenAiVoiceFor(VoiceLocale.SYSTEM),
    val elevenLabsVoice: String = UserPreferences.DEFAULT_ELEVENLABS_VOICE,
    val voiceLocale: VoiceLocale = VoiceLocale.SYSTEM,
    val useCalendarEvents: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val openAiKeyConfigured: Boolean = false,
    val elevenLabsKeyConfigured: Boolean = false,
)
