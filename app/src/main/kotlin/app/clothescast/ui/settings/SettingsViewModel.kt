package app.clothescast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.core.domain.model.WardrobeRule
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val keyStore: SecureKeyStore,
    private val rearmAlarm: (Schedule, ForecastPeriod) -> Unit,
    private val cancelAlarm: (ForecastPeriod) -> Unit,
    private val geocodingClient: OpenMeteoGeocodingClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

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
                        deliveryMode = prefs.deliveryMode,
                        temperatureUnit = prefs.temperatureUnit,
                        distanceUnit = prefs.distanceUnit,
                        wardrobeRules = prefs.wardrobeRules,
                        location = prefs.location,
                        useDeviceLocation = prefs.useDeviceLocation,
                        ttsEngine = prefs.ttsEngine,
                        geminiVoice = prefs.geminiVoice,
                        openAiVoice = prefs.openAiVoice,
                        elevenLabsVoice = prefs.elevenLabsVoice,
                        voiceLocale = prefs.voiceLocale,
                        useCalendarEvents = prefs.useCalendarEvents,
                        region = prefs.region,
                    )
                }
            }
        }
        viewModelScope.launch { refreshApiKeyStatus() }
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

    fun setElevenLabsVoice(voice: String) {
        viewModelScope.launch { settingsRepository.setElevenLabsVoice(voice) }
    }

    fun setDeliveryMode(mode: DeliveryMode) {
        viewModelScope.launch { settingsRepository.setDeliveryMode(mode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.setTemperatureUnit(unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settingsRepository.setDistanceUnit(unit) }
    }

    fun addWardrobeRule(rule: WardrobeRule) {
        viewModelScope.launch {
            settingsRepository.setWardrobeRules(_state.value.wardrobeRules + rule)
        }
    }

    fun replaceWardrobeRule(index: Int, rule: WardrobeRule) {
        viewModelScope.launch {
            val current = _state.value.wardrobeRules
            if (index !in current.indices) return@launch
            settingsRepository.setWardrobeRules(current.toMutableList().apply { this[index] = rule })
        }
    }

    fun deleteWardrobeRule(index: Int) {
        viewModelScope.launch {
            val current = _state.value.wardrobeRules
            if (index !in current.indices) return@launch
            settingsRepository.setWardrobeRules(current.toMutableList().apply { removeAt(index) })
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

    fun setRegion(region: Region) {
        viewModelScope.launch { settingsRepository.setRegion(region) }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return SettingsViewModel(settingsRepository, keyStore, rearmAlarm, cancelAlarm, geocodingClient) as T
        }
    }
}
