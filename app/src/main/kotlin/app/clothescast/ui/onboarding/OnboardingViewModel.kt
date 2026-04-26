package app.clothescast.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.domain.model.Location
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingState(
    val geminiKeyConfigured: Boolean = false,
    val location: Location? = null,
    val useDeviceLocation: Boolean = false,
)

/**
 * Drives the first-run onboarding screen. Exposes the slice of settings the
 * onboarding's checkmarks track (Gemini key, location) and the setters its
 * inline editors need. Notification permission is handled directly in the
 * composable — it requires Context and lifecycle observation, which are
 * awkward to plumb through a ViewModel.
 */
class OnboardingViewModel(
    private val secureKeyStore: SecureKeyStore,
    private val settingsRepository: SettingsRepository,
    private val geocodingClient: OpenMeteoGeocodingClient,
) : ViewModel() {

    val state: StateFlow<OnboardingState> = combine(
        secureKeyStore.geminiKeyConfiguredFlow,
        settingsRepository.preferences,
    ) { keyConfigured, prefs ->
        OnboardingState(
            geminiKeyConfigured = keyConfigured,
            location = prefs.location,
            useDeviceLocation = prefs.useDeviceLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    fun setApiKey(key: String) {
        viewModelScope.launch { secureKeyStore.set(key.trim()) }
    }

    fun setUseDeviceLocation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDeviceLocation(enabled) }
    }

    fun selectLocation(location: Location) {
        viewModelScope.launch { settingsRepository.setLocation(location) }
    }

    suspend fun searchLocations(query: String): List<Location> = geocodingClient.search(query)

    class Factory(
        private val secureKeyStore: SecureKeyStore,
        private val settingsRepository: SettingsRepository,
        private val geocodingClient: OpenMeteoGeocodingClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return OnboardingViewModel(
                secureKeyStore = secureKeyStore,
                settingsRepository = settingsRepository,
                geocodingClient = geocodingClient,
            ) as T
        }
    }
}
