package app.adaptweather.ui.settings

import app.adaptweather.core.domain.model.DeliveryMode
import app.adaptweather.core.domain.model.DistanceUnit
import app.adaptweather.core.domain.model.Location
import app.adaptweather.core.domain.model.Schedule
import app.adaptweather.core.domain.model.TemperatureUnit
import app.adaptweather.core.domain.model.TtsEngine
import app.adaptweather.core.domain.model.UserPreferences
import app.adaptweather.core.domain.model.VoiceLocale
import app.adaptweather.core.domain.model.WardrobeRule
import app.adaptweather.tts.defaultOpenAiVoiceFor
import java.time.DayOfWeek
import java.time.LocalTime

/** What [SettingsScreen] needs to render. */
data class SettingsState(
    val scheduleTime: LocalTime = LocalTime.of(7, 0),
    val scheduleDays: Set<DayOfWeek> = Schedule.EVERY_DAY,
    val deliveryMode: DeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val wardrobeRules: List<WardrobeRule> = WardrobeRule.DEFAULTS,
    val location: Location? = null,
    val useDeviceLocation: Boolean = false,
    val ttsEngine: TtsEngine = TtsEngine.DEVICE,
    val geminiVoice: String = UserPreferences.DEFAULT_GEMINI_VOICE,
    // Match SettingsRepository's locale-aware default so the picker doesn't
    // briefly render "alloy" before the first DataStore emission overrides it.
    // When voiceLocale is SYSTEM (the default), this resolves through the phone's
    // current locale → fable on en-GB, nova everywhere else.
    val openAiVoice: String = defaultOpenAiVoiceFor(VoiceLocale.SYSTEM),
    val voiceLocale: VoiceLocale = VoiceLocale.SYSTEM,
    val apiKeyConfigured: Boolean = false,
    val openAiKeyConfigured: Boolean = false,
)
