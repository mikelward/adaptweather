package app.clothescast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.data.tts.ElevenLabsTtsClient
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import app.clothescast.diag.DiagLog
import app.clothescast.tts.TtsVoiceEnumerator
import app.clothescast.tts.resolve
import app.clothescast.tts.toVoiceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val keyStore: SecureKeyStore,
    private val rearmAlarm: (Schedule, ForecastPeriod) -> Unit,
    private val cancelAlarm: (ForecastPeriod) -> Unit,
    private val geocodingClient: OpenMeteoGeocodingClient,
    private val voiceEnumerator: TtsVoiceEnumerator,
    private val elevenLabsTtsClient: ElevenLabsTtsClient? = null,
    /**
     * Surfaces refresh failures to the user. Defaulted to a no-op so existing
     * tests that don't exercise refresh don't have to wire it up; the
     * Activity passes a Toast-backed implementation.
     */
    private val showError: (String) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /**
     * Tracks the most recent device-voice enumeration so we can cancel it
     * when the locale changes mid-load — avoids a stale en-US list landing
     * after the user has already switched to en-GB.
     */
    private var deviceVoiceLoadJob: Job? = null
    private var lastEnumeratedLocale: VoiceLocale? = null

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        scheduleTime = prefs.schedule.time,
                        scheduleDays = prefs.schedule.days,
                        tonightTime = prefs.tonightSchedule.time,
                        tonightDays = prefs.tonightSchedule.days,
                        tonightEnabled = prefs.tonightEnabled,
                        tonightNotifyOnlyOnEvents = prefs.tonightNotifyOnlyOnEvents,
                        deliveryMode = prefs.deliveryMode,
                        tonightDeliveryMode = prefs.tonightDeliveryMode,
                        dailyMentionEveningEvents = prefs.dailyMentionEveningEvents,
                        region = prefs.region,
                        temperatureUnit = prefs.temperatureUnit,
                        distanceUnit = prefs.distanceUnit,
                        clothesRules = prefs.clothesRules,
                        location = prefs.location,
                        useDeviceLocation = prefs.useDeviceLocation,
                        ttsEngine = prefs.ttsEngine,
                        geminiVoice = prefs.geminiVoice,
                        openAiVoice = prefs.openAiVoice,
                        openAiSpeed = prefs.openAiSpeed,
                        elevenLabsVoice = prefs.elevenLabsVoice,
                        elevenLabsModel = prefs.elevenLabsModel,
                        elevenLabsSpeed = prefs.elevenLabsSpeed,
                        elevenLabsStability = prefs.elevenLabsStability,
                        deviceVoice = prefs.deviceVoice,
                        voiceLocale = prefs.voiceLocale,
                        useCalendarEvents = prefs.useCalendarEvents,
                    )
                }
                // Re-enumerate on first observation and any locale flip; the
                // engine reports a different "exact match" set per locale.
                if (lastEnumeratedLocale != prefs.voiceLocale) {
                    lastEnumeratedLocale = prefs.voiceLocale
                    refreshDeviceVoices(prefs.voiceLocale)
                }
            }
        }
        viewModelScope.launch { refreshApiKeyStatus() }
    }

    /**
     * Reloads the device-voice picker list for [locale] and resolves the
     * "currently using" indicator. [pinnedIdOverride] lets [setDeviceVoice]
     * pass the just-written pin so the indicator doesn't briefly resolve
     * against the previous pin while the preferences flow catches up; the
     * default reads the current state.
     */
    private fun refreshDeviceVoices(locale: VoiceLocale, pinnedIdOverride: String? = _state.value.deviceVoice) {
        deviceVoiceLoadJob?.cancel()
        deviceVoiceLoadJob = viewModelScope.launch {
            // All three enumerator calls bind the engine, which is JNI work
            // — keep them off the main dispatcher.
            val resolvedLocale = locale.resolve()
            val voices = withContext(Dispatchers.IO) {
                runCatching { voiceEnumerator.listVoices(resolvedLocale) }.getOrDefault(emptyList())
            }
            val pinnedId = pinnedIdOverride
            val effective = if (pinnedId != null) {
                // Fast path: pin is in the locale-filtered list. Slow path:
                // pin is from a different locale variant within the same
                // language (the speaker accepts these too). We only fall
                // through to the second engine bind when the fast path
                // misses, which is rare — most pins match the current
                // locale.
                voices.firstOrNull { it.id == pinnedId }
                    ?: withContext(Dispatchers.IO) {
                        runCatching { voiceEnumerator.findVoice(pinnedId) }.getOrNull()
                    }
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { voiceEnumerator.resolveAutoPick(resolvedLocale) }.getOrNull()
                }
            }
            _state.update { it.copy(deviceVoices = voices, effectiveDeviceVoice = effective) }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            keyStore.set(key.trim())
            refreshApiKeyStatus()
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            keyStore.clear()
            refreshApiKeyStatus()
        }
    }

    fun setOpenAiKey(key: String) {
        viewModelScope.launch {
            keyStore.setOpenAi(key.trim())
            refreshApiKeyStatus()
        }
    }

    fun clearOpenAiKey() {
        viewModelScope.launch {
            keyStore.clearOpenAi()
            refreshApiKeyStatus()
        }
    }

    fun setElevenLabsKey(key: String) {
        viewModelScope.launch {
            keyStore.setElevenLabs(key.trim())
            refreshApiKeyStatus()
        }
    }

    fun clearElevenLabsKey() {
        viewModelScope.launch {
            keyStore.clearElevenLabs()
            refreshApiKeyStatus()
        }
    }

    fun setGeminiVoice(voice: String) {
        viewModelScope.launch { settingsRepository.setGeminiVoice(voice) }
    }

    fun setOpenAiVoice(voice: String) {
        viewModelScope.launch { settingsRepository.setOpenAiVoice(voice) }
    }

    fun setOpenAiSpeed(speed: Double) {
        viewModelScope.launch { settingsRepository.setOpenAiSpeed(speed) }
    }

    fun setElevenLabsVoice(voice: String) {
        viewModelScope.launch { settingsRepository.setElevenLabsVoice(voice) }
    }

    fun setElevenLabsModel(model: String) {
        viewModelScope.launch { settingsRepository.setElevenLabsModel(model) }
    }

    fun setElevenLabsSpeed(speed: Double) {
        viewModelScope.launch { settingsRepository.setElevenLabsSpeed(speed) }
    }

    fun setElevenLabsStability(stability: Double) {
        viewModelScope.launch { settingsRepository.setElevenLabsStability(stability) }
    }

    /**
     * Hits `GET /v1/voices` with the stored ElevenLabs key and replaces the
     * picker's voice list with whatever the user's account exposes —
     * premade library plus their own clones / generated voices. No-ops if
     * the key isn't configured (the UI also gates the button), if a refresh
     * is already in flight, or if the client wasn't injected (test wiring).
     *
     * Failures surface through [showError] (the Activity wires a Toast)
     * and leave the picker on whatever list it was already showing — we
     * don't wipe a previous successful refresh because the network blipped.
     * Coroutine cancellation (ViewModel cleared, navigation away) is *not*
     * surfaced as a user-visible error — we re-throw so structured
     * concurrency unwinds cleanly.
     */
    fun refreshElevenLabsVoices() {
        val client = elevenLabsTtsClient ?: return
        val current = _state.value
        if (!current.elevenLabsKeyConfigured || current.elevenLabsRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(elevenLabsRefreshing = true) }
            try {
                val voices = withContext(Dispatchers.IO) { client.listVoices() }
                _state.update { it.copy(elevenLabsRefreshedVoices = voices.toVoiceOptions()) }
            } catch (t: Throwable) {
                // Cancellation is a normal lifecycle signal (navigation
                // away, ViewModel cleared) — re-throwing lets the
                // coroutine machinery unwind without flashing a Toast at
                // the user.
                if (t is kotlinx.coroutines.CancellationException) throw t
                DiagLog.w(TAG, "ElevenLabs voice refresh failed", t)
                val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                showError(message)
            } finally {
                // `finally` rather than per-branch updates so the spinner
                // always clears, including on cancellation.
                _state.update { it.copy(elevenLabsRefreshing = false) }
            }
        }
    }

    fun setDeviceVoice(voice: String?) {
        viewModelScope.launch {
            // Update _state synchronously first so the picker reflects the
            // new pin in the same frame, and so refreshDeviceVoices below
            // resolves the "currently using" line against the *new* pin
            // rather than the previous one. The DataStore emission that
            // arrives a few hops later is idempotent.
            _state.update { it.copy(deviceVoice = voice) }
            settingsRepository.setDeviceVoice(voice)
            refreshDeviceVoices(_state.value.voiceLocale, pinnedIdOverride = voice)
        }
    }

    fun setDeliveryMode(mode: DeliveryMode) {
        viewModelScope.launch { settingsRepository.setDeliveryMode(mode) }
    }

    fun setTonightDeliveryMode(mode: DeliveryMode) {
        viewModelScope.launch { settingsRepository.setTonightDeliveryMode(mode) }
    }

    fun setRegion(region: Region) {
        viewModelScope.launch { settingsRepository.setRegion(region) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.setTemperatureUnit(unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settingsRepository.setDistanceUnit(unit) }
    }

    fun addClothesRule(rule: ClothesRule) {
        viewModelScope.launch {
            settingsRepository.setClothesRules(_state.value.clothesRules + rule)
        }
    }

    fun replaceClothesRule(index: Int, rule: ClothesRule) {
        viewModelScope.launch {
            val current = _state.value.clothesRules
            if (index !in current.indices) return@launch
            settingsRepository.setClothesRules(current.toMutableList().apply { this[index] = rule })
        }
    }

    fun deleteClothesRule(index: Int) {
        viewModelScope.launch {
            val current = _state.value.clothesRules
            if (index !in current.indices) return@launch
            settingsRepository.setClothesRules(current.toMutableList().apply { removeAt(index) })
        }
    }

    fun selectLocation(location: Location) {
        viewModelScope.launch { settingsRepository.setLocation(location) }
    }

    fun clearLocation() {
        viewModelScope.launch { settingsRepository.clearLocation() }
    }

    fun setUseDeviceLocation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDeviceLocation(enabled) }
    }

    fun setTtsEngine(engine: TtsEngine) {
        viewModelScope.launch { settingsRepository.setTtsEngine(engine) }
    }

    fun setVoiceLocale(locale: VoiceLocale) {
        viewModelScope.launch { settingsRepository.setVoiceLocale(locale) }
    }

    fun setUseCalendarEvents(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseCalendarEvents(enabled) }
    }

    /** Used by the data-sources page's location dialog; safe to call from any dispatcher. */
    suspend fun searchLocations(query: String): List<Location> = geocodingClient.search(query)

    fun setSchedule(time: LocalTime, days: Set<DayOfWeek>) {
        if (days.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.setSchedule(time, days)
            // Re-arm the alarm immediately so the next occurrence picks up the new wall-clock.
            // The repository resolves zoneId fresh on each emission, so the schedule we read
            // back is the new one with the current zone.
            val updated = settingsRepository.preferences.first().schedule
            rearmAlarm(updated, ForecastPeriod.TODAY)
        }
    }

    fun setTonightSchedule(time: LocalTime, days: Set<DayOfWeek>) {
        if (days.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.setTonightSchedule(time, days)
            val prefs = settingsRepository.preferences.first()
            // Don't arm the alarm if tonight is disabled — would just trigger an
            // ignored worker run.
            if (prefs.tonightEnabled) rearmAlarm(prefs.tonightSchedule, ForecastPeriod.TONIGHT)
        }
    }

    fun setTonightNotifyOnlyOnEvents(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTonightNotifyOnlyOnEvents(enabled) }
    }

    fun setDailyMentionEveningEvents(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDailyMentionEveningEvents(enabled) }
    }

    fun setTonightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTonightEnabled(enabled)
            val prefs = settingsRepository.preferences.first()
            if (prefs.tonightEnabled) {
                rearmAlarm(prefs.tonightSchedule, ForecastPeriod.TONIGHT)
            } else {
                cancelAlarm(ForecastPeriod.TONIGHT)
            }
        }
    }

    private suspend fun refreshApiKeyStatus() {
        val gemini = runCatching { keyStore.get().isNotBlank() }.getOrDefault(false)
        val openAi = runCatching { keyStore.getOpenAi().isNotBlank() }.getOrDefault(false)
        val elevenLabs = runCatching { keyStore.getElevenLabs().isNotBlank() }.getOrDefault(false)
        _state.update {
            it.copy(
                apiKeyConfigured = gemini,
                openAiKeyConfigured = openAi,
                elevenLabsKeyConfigured = elevenLabs,
            )
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val keyStore: SecureKeyStore,
        private val rearmAlarm: (Schedule, ForecastPeriod) -> Unit,
        private val cancelAlarm: (ForecastPeriod) -> Unit,
        private val geocodingClient: OpenMeteoGeocodingClient,
        private val voiceEnumerator: TtsVoiceEnumerator,
        private val elevenLabsTtsClient: ElevenLabsTtsClient,
        private val showError: (String) -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return SettingsViewModel(
                settingsRepository,
                keyStore,
                rearmAlarm,
                cancelAlarm,
                geocodingClient,
                voiceEnumerator,
                elevenLabsTtsClient,
                showError,
            ) as T
        }
    }

    private companion object {
        private const val TAG = "SettingsViewModel"
    }
}
