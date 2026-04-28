package app.clothescast.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import app.clothescast.tts.defaultOpenAiVoiceFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Persists [UserPreferences] in DataStore Preferences.
 *
 * `Schedule.zoneId` is intentionally NOT persisted — it's resolved from
 * [zoneIdProvider] (defaulting to the current system zone) every time the flow
 * emits. This way, if the user travels or DST flips, the next read picks up the
 * correct zone without us having to migrate stored data.
 *
 * Constructor takes the [DataStore] for unit-test injection;
 * [SettingsRepository.create] is the production factory.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val systemLocaleProvider: () -> Locale = { Locale.getDefault() },
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs -> prefs.toUserPreferences() }

    suspend fun setSchedule(time: LocalTime, days: Set<DayOfWeek>) {
        require(days.isNotEmpty()) { "Schedule must include at least one day" }
        dataStore.edit { prefs ->
            prefs[SCHEDULE_TIME] = TIME_FORMAT.format(time)
            prefs[SCHEDULE_DAYS] = days.map { it.name }.toSet()
        }
    }

    suspend fun setTonightSchedule(time: LocalTime, days: Set<DayOfWeek>) {
        require(days.isNotEmpty()) { "Schedule must include at least one day" }
        dataStore.edit { prefs ->
            prefs[TONIGHT_TIME] = TIME_FORMAT.format(time)
            prefs[TONIGHT_DAYS] = days.map { it.name }.toSet()
        }
    }

    suspend fun setTonightEnabled(enabled: Boolean) {
        dataStore.edit { it[TONIGHT_ENABLED] = enabled }
    }

    suspend fun setDeliveryMode(mode: DeliveryMode) {
        dataStore.edit { it[DELIVERY_MODE] = mode.name }
    }

    suspend fun setTonightDeliveryMode(mode: DeliveryMode) {
        dataStore.edit { it[TONIGHT_DELIVERY_MODE] = mode.name }
    }

    suspend fun setRegion(region: Region) {
        dataStore.edit { it[REGION] = region.name }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { it[DISTANCE_UNIT] = unit.name }
    }

    suspend fun setClothesRules(rules: List<ClothesRule>) {
        dataStore.edit { it[CLOTHES_RULES] = json.encodeToString(rules.map { rule -> rule.toDto() }) }
    }

    suspend fun setLocation(location: Location) {
        dataStore.edit { prefs ->
            prefs[LOCATION_LAT] = location.latitude
            prefs[LOCATION_LON] = location.longitude
            location.displayName?.let { prefs[LOCATION_NAME] = it } ?: prefs.remove(LOCATION_NAME)
        }
    }

    suspend fun clearLocation() {
        dataStore.edit { prefs ->
            prefs.remove(LOCATION_LAT)
            prefs.remove(LOCATION_LON)
            prefs.remove(LOCATION_NAME)
        }
    }

    suspend fun setUseDeviceLocation(enabled: Boolean) {
        dataStore.edit { it[USE_DEVICE_LOCATION] = enabled }
    }

    suspend fun setTtsEngine(engine: TtsEngine) {
        dataStore.edit { it[TTS_ENGINE] = engine.name }
    }

    suspend fun setGeminiVoice(voice: String) {
        dataStore.edit { it[GEMINI_VOICE] = voice }
    }

    suspend fun setOpenAiVoice(voice: String) {
        dataStore.edit { it[OPENAI_VOICE] = voice }
    }

    suspend fun setElevenLabsVoice(voice: String) {
        dataStore.edit { it[ELEVENLABS_VOICE] = voice }
    }

    suspend fun setVoiceLocale(locale: VoiceLocale) {
        dataStore.edit { it[VOICE_LOCALE] = locale.name }
    }

    suspend fun setUseCalendarEvents(enabled: Boolean) {
        dataStore.edit { it[USE_CALENDAR_EVENTS] = enabled }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val time = this[SCHEDULE_TIME]?.let { LocalTime.parse(it, TIME_FORMAT) }
            ?: DEFAULT_TIME
        val days = this[SCHEDULE_DAYS]?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: Schedule.EVERY_DAY
        val deliveryMode = this[DELIVERY_MODE]?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
            ?: DeliveryMode.NOTIFICATION_ONLY
        // Tonight's mode falls back to [deliveryMode] when absent so existing
        // installs keep the old "shared mode" behaviour until the user
        // explicitly diverges the two cards in Settings.
        val tonightDeliveryMode = this[TONIGHT_DELIVERY_MODE]?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
            ?: deliveryMode
        val region = this[REGION]?.let { runCatching { Region.valueOf(it) }.getOrNull() }
            ?: Region.SYSTEM
        // Resolve units off the user's region — SYSTEM falls through to the
        // phone locale. Once they explicitly pick a unit it sticks regardless
        // of region (mirrors how openAiVoice handles voiceLocale).
        val regionLocale = region.toJavaLocale() ?: systemLocaleProvider()
        val temperatureUnit = this[TEMPERATURE_UNIT]?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() }
            ?: defaultTemperatureUnitFor(regionLocale)
        val distanceUnit = this[DISTANCE_UNIT]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
            ?: defaultDistanceUnitFor(regionLocale)
        val rules = parseRules(this[CLOTHES_RULES])
        val location = parseLocation(this)
        val useDeviceLocation = this[USE_DEVICE_LOCATION] == true
        val ttsEngine = this[TTS_ENGINE]?.let { runCatching { TtsEngine.valueOf(it) }.getOrNull() }
            ?: TtsEngine.DEVICE
        val geminiVoice = this[GEMINI_VOICE]?.takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_GEMINI_VOICE
        val voiceLocale = this[VOICE_LOCALE]?.let { runCatching { VoiceLocale.valueOf(it) }.getOrNull() }
            ?: VoiceLocale.SYSTEM
        // OpenAI default depends on locale (en-GB → fable; everything else → nova).
        // Resolve only after voiceLocale is known so the picked voice matches.
        val openAiVoice = this[OPENAI_VOICE]?.takeIf { it.isNotBlank() }
            ?: defaultOpenAiVoiceFor(voiceLocale)
        val elevenLabsVoice = this[ELEVENLABS_VOICE]?.takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_ELEVENLABS_VOICE
        val useCalendarEvents = this[USE_CALENDAR_EVENTS] == true
        val tonightTime = this[TONIGHT_TIME]?.let { LocalTime.parse(it, TIME_FORMAT) }
            ?: DEFAULT_TONIGHT_TIME
        val tonightDays = this[TONIGHT_DAYS]?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: Schedule.EVERY_DAY
        // Default-on: existing installs that haven't seen the tonight pref yet get
        // the silent overnight notification (it's quiet by default when there are
        // no calendar events, so it's not noisy out of the box).
        val tonightEnabled = this[TONIGHT_ENABLED] != false
        val zone = zoneIdProvider()

        return UserPreferences(
            schedule = Schedule(time = time, days = days, zoneId = zone),
            deliveryMode = deliveryMode,
            region = region,
            temperatureUnit = temperatureUnit,
            distanceUnit = distanceUnit,
            clothesRules = rules,
            location = location,
            useDeviceLocation = useDeviceLocation,
            ttsEngine = ttsEngine,
            geminiVoice = geminiVoice,
            openAiVoice = openAiVoice,
            elevenLabsVoice = elevenLabsVoice,
            voiceLocale = voiceLocale,
            useCalendarEvents = useCalendarEvents,
            tonightSchedule = Schedule(time = tonightTime, days = tonightDays, zoneId = zone),
            tonightEnabled = tonightEnabled,
            tonightDeliveryMode = tonightDeliveryMode,
        )
    }

    private fun parseLocation(prefs: Preferences): Location? {
        val lat = prefs[LOCATION_LAT] ?: return null
        val lon = prefs[LOCATION_LON] ?: return null
        return runCatching {
            Location(
                latitude = lat,
                longitude = lon,
                displayName = prefs[LOCATION_NAME],
            )
        }.getOrNull()
    }

    private fun parseRules(raw: String?): List<ClothesRule> {
        if (raw.isNullOrBlank()) return ClothesRule.DEFAULTS
        return runCatching {
            json.decodeFromString<List<ClothesRuleDto>>(raw).map { it.toDomain() }
        }.getOrDefault(ClothesRule.DEFAULTS)
            // An empty stored list is also treated as "no rules configured" rather
            // than honoured as an intentional zero — with editing locked
            // (ClothesSettings is read-only), a user who deleted all their rules
            // in a previous editable-UI version would otherwise have no way to
            // recover the defaults.
            .ifEmpty { ClothesRule.DEFAULTS }
    }

    companion object {
        private val SCHEDULE_TIME = stringPreferencesKey("schedule_time_hhmm")
        private val SCHEDULE_DAYS = stringSetPreferencesKey("schedule_days")
        private val DELIVERY_MODE = stringPreferencesKey("delivery_mode")
        private val REGION = stringPreferencesKey("region")
        private val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val CLOTHES_RULES = stringPreferencesKey("clothes_rules_json")
        private val LOCATION_LAT = doublePreferencesKey("location_latitude")
        private val LOCATION_LON = doublePreferencesKey("location_longitude")
        private val LOCATION_NAME = stringPreferencesKey("location_display_name")
        private val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        private val TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val GEMINI_VOICE = stringPreferencesKey("gemini_voice")
        private val OPENAI_VOICE = stringPreferencesKey("openai_voice")
        private val ELEVENLABS_VOICE = stringPreferencesKey("elevenlabs_voice")
        private val VOICE_LOCALE = stringPreferencesKey("voice_locale")
        private val USE_CALENDAR_EVENTS = booleanPreferencesKey("use_calendar_events")
        private val TONIGHT_TIME = stringPreferencesKey("tonight_time_hhmm")
        private val TONIGHT_DAYS = stringSetPreferencesKey("tonight_days")
        private val TONIGHT_ENABLED = booleanPreferencesKey("tonight_enabled")
        private val TONIGHT_DELIVERY_MODE = stringPreferencesKey("tonight_delivery_mode")

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)
        private val DEFAULT_TONIGHT_TIME: LocalTime = LocalTime.of(19, 0)

        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.settingsDataStore)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private fun Region.toJavaLocale(): Locale? = bcp47?.let { Locale.forLanguageTag(it) }

// Only the US uses Fahrenheit in everyday weather contexts. A handful of
// dependencies (BS, BZ, KY, PW) also do, but they're rounding error and the
// user can override via the unit picker if needed — not worth the extra surface.
// Internal so SettingsState can mirror the repository's defaults at construction
// time, before the first DataStore emission lands.
internal fun defaultTemperatureUnitFor(locale: Locale): TemperatureUnit =
    if (locale.country == "US") TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS

// Only the US sticks with imperial for weather-app distance contexts (wind
// speed in mph, precipitation in inches). UK uses miles for *roads* but km/h
// and mm for weather, so it lands on the metric default with everyone else.
internal fun defaultDistanceUnitFor(locale: Locale): DistanceUnit =
    if (locale.country == "US") DistanceUnit.MILES else DistanceUnit.KILOMETERS
