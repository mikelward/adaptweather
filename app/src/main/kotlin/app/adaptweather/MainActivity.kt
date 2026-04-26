package app.adaptweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import app.adaptweather.notification.NotificationPermission
import app.adaptweather.ui.onboarding.OnboardingScreen
import app.adaptweather.ui.onboarding.OnboardingViewModel
import app.adaptweather.ui.settings.SettingsRoute
import app.adaptweather.ui.settings.SettingsScreen
import app.adaptweather.ui.settings.SettingsViewModel
import app.adaptweather.ui.theme.AdaptWeatherTheme
import app.adaptweather.ui.today.TodayScreen
import app.adaptweather.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private enum class Screen { Today, Settings, Onboarding }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AdaptWeatherApplication
        setContent {
            AdaptWeatherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AdaptWeatherNav(app)
                }
            }
        }
    }
}

@Composable
private fun AdaptWeatherNav(app: AdaptWeatherApplication) {
    val context = LocalContext.current

    // Decide initial screen once on first composition. Permission checks are sync;
    // DataStore reads (Gemini key + preferences) go through one Preferences fetch
    // each, microseconds in practice — runBlocking here keeps the UX flicker-free
    // (no flash of Today before snapping to Onboarding) at a negligible startup cost.
    val initialScreen = remember {
        val notificationOk = NotificationPermission.isGranted(context)
        val keyOk = runBlocking { app.secureKeyStore.geminiKeyConfiguredFlow.first() }
        val prefs = runBlocking { app.settingsRepository.preferences.first() }
        // Location is "configured" if either branch is filled in — device-location
        // toggle on (with permission) or a manual city stored.
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val locationOk = (prefs.useDeviceLocation && coarseGranted) || prefs.location != null
        if (notificationOk && keyOk && locationOk) Screen.Today else Screen.Onboarding
    }

    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    // Holds the SettingsRoute we want to land on when entering Settings programmatically
    // (e.g. from onboarding's "Continue"). Saved as a name string so rememberSaveable
    // doesn't need a custom Saver. Consumed once and reset to null on the way out.
    var settingsInitialRoute by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = screen == Screen.Settings) {
        screen = Screen.Today
        settingsInitialRoute = null
    }

    when (screen) {
        Screen.Today -> {
            val today: TodayViewModel = viewModel(
                factory = TodayViewModel.Factory(
                    insightCache = app.insightCache,
                    workManager = WorkManager.getInstance(app),
                    settingsRepository = app.settingsRepository,
                ),
            )
            TodayScreen(
                viewModel = today,
                onNavigateToSettings = {
                    settingsInitialRoute = null
                    screen = Screen.Settings
                },
            )
        }
        Screen.Settings -> {
            val settings: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    settingsRepository = app.settingsRepository,
                    keyStore = app.secureKeyStore,
                    rearmAlarm = app.dailyAlarmScheduler::schedule,
                    geocodingClient = app.geocodingClient,
                ),
            )
            SettingsScreen(
                viewModel = settings,
                onNavigateBack = {
                    screen = Screen.Today
                    settingsInitialRoute = null
                },
                initialRoute = settingsInitialRoute
                    ?.let { runCatching { SettingsRoute.valueOf(it) }.getOrNull() },
            )
        }
        Screen.Onboarding -> {
            val onboarding: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(
                    secureKeyStore = app.secureKeyStore,
                    settingsRepository = app.settingsRepository,
                    geocodingClient = app.geocodingClient,
                ),
            )
            OnboardingScreen(
                viewModel = onboarding,
                onContinue = {
                    settingsInitialRoute = SettingsRoute.Schedule.name
                    screen = Screen.Settings
                },
                onSkip = {
                    settingsInitialRoute = null
                    screen = Screen.Today
                },
            )
        }
    }
}
