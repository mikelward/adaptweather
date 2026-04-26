package app.adaptweather.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.adaptweather.data.SecureKeyStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingState(
    val geminiKeyConfigured: Boolean = false,
)

/**
 * Drives the first-run onboarding screen. Owns the Gemini API key entry; notification
 * permission is handled directly in the composable (it requires Context and lifecycle
 * observation, which are awkward to plumb through a ViewModel).
 */
class OnboardingViewModel(
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    val state: StateFlow<OnboardingState> = secureKeyStore.geminiKeyConfiguredFlow
        .map { OnboardingState(geminiKeyConfigured = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    fun setApiKey(key: String) {
        viewModelScope.launch { secureKeyStore.set(key.trim()) }
    }

    class Factory(
        private val secureKeyStore: SecureKeyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return OnboardingViewModel(secureKeyStore) as T
        }
    }
}
