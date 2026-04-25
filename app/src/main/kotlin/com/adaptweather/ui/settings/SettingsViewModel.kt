package com.adaptweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val keyStore: SecureKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        deliveryMode = prefs.deliveryMode,
                        temperatureUnit = prefs.temperatureUnit,
                        distanceUnit = prefs.distanceUnit,
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

    fun setDeliveryMode(mode: DeliveryMode) {
        viewModelScope.launch { settingsRepository.setDeliveryMode(mode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.setUnits(unit, _state.value.distanceUnit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settingsRepository.setUnits(_state.value.temperatureUnit, unit) }
    }

    private suspend fun refreshApiKeyStatus() {
        val configured = runCatching { keyStore.get().isNotBlank() }.getOrDefault(false)
        _state.update { it.copy(apiKeyConfigured = configured) }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val keyStore: SecureKeyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return SettingsViewModel(settingsRepository, keyStore) as T
        }
    }
}
