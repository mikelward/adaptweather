package app.clothescast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import app.clothescast.notification.NotificationPermission
import app.clothescast.ui.isTelevision
import app.clothescast.ui.onboarding.OnboardingScreen
import app.clothescast.ui.onboarding.OnboardingViewModel
import app.clothescast.ui.pairing.PairingScreen
import app.clothescast.ui.pairing.PairingViewModel
import app.clothescast.ui.settings.SettingsRoute
import app.clothescast.ui.settings.SettingsScreen
import app.clothescast.ui.settings.SettingsViewModel
import app.clothescast.ui.theme.ClothesCastTheme
import app.clothescast.ui.today.TodayScreen
import app.clothescast.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private enum class Screen { Today, Settings, Onboarding, Pairing }

class MainActivity : ComponentActivity() {
    // Incremented every time a notification tap delivers EXTRA_NAVIGATE_TO_TODAY —
    // both via onNewIntent (activity already running) and via the launching intent
    // in onCreate (cold start / activity recreated after process death, where
    // rememberSaveable would otherwise restore the previously-saved screen, e.g.
    // Settings). ClothesCastNav observes this counter and snaps back to Today
    // whenever it ticks, so a notification tap reliably lands the user on Today
    // regardless of cold/warm start.
    private var navigateToTodayVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_TODAY, false) == true) {
            navigateToTodayVersion++
        }
        val app = application as ClothesCastApplication
        setContent {
            ClothesCastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ClothesCastNav(app, navigateToTodayVersion)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_TODAY, false)) {
            navigateToTodayVersion++
        }
    }

    companion object {
        /** Extra set by all notification tap intents. MainActivity increments its
         *  navigation counter when this is present so ClothesCastNav snaps to Today. */
        const val EXTRA_NAVIGATE_TO_TODAY = "navigate_to_today"
    }
}

@Composable
private fun ClothesCastNav(app: ClothesCastApplication, navigateToTodayVersion: Int) {
    val context = LocalContext.current

    // Decide initial screen once on first composition. Permission checks are sync;
    // DataStore reads (Gemini key + preferences) go through one Preferences fetch
    // each, microseconds in practice — runBlocking here keeps the UX flicker-free
    // (no flash of Today before snapping to Onboarding) at a negligible startup cost.
    val initialScreen = remember {
        val tv = isTelevision(context)
        // TV OS does not expose POST_NOTIFICATIONS or GPS-based location; skip
        // both checks so a configured-key + city TV install goes straight to Today.
        val notificationOk = tv || NotificationPermission.isGranted(context)
        val keyOk = runBlocking { app.secureKeyStore.geminiKeyConfiguredFlow.first() }
        val prefs = runBlocking { app.settingsRepository.preferences.first() }
        val locationOk = if (tv) {
            // On TV only a manually picked city counts — device location is unavailable.
            prefs.location != null
        } else {
            // Location is "configured" if either branch is filled in — device-location
            // toggle on (with permission) or a manual city stored.
            val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            (prefs.useDeviceLocation && coarseGranted) || prefs.location != null
        }
        if (notificationOk && keyOk && locationOk) Screen.Today else Screen.Onboarding
    }

    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    // Holds the SettingsRoute we want to land on when entering Settings programmatically
    // (e.g. from onboarding's "Continue"). Saved as a name string so rememberSaveable
    // doesn't need a custom Saver. Consumed once and reset to null on the way out.
    var settingsInitialRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // When the user taps a notification while the app is already running, MainActivity
    // increments navigateToTodayVersion via onNewIntent. Snap back to Today so the
    // user always lands on the screen that actually shows the insight/alert they tapped.
    LaunchedEffect(navigateToTodayVersion) {
        if (navigateToTodayVersion > 0) {
            screen = Screen.Today
            settingsInitialRoute = null
        }
    }

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
                onNavigateToAbout = {
                    settingsInitialRoute = SettingsRoute.About.name
                    screen = Screen.Settings
                },
                onNavigateToDataSources = {
                    settingsInitialRoute = SettingsRoute.DataSources.name
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
                    cancelAlarm = app.dailyAlarmScheduler::cancel,
                    geocodingClient = app.geocodingClient,
                    voiceEnumerator = app.androidTtsVoiceEnumerator,
                    elevenLabsTtsClient = app.elevenLabsTtsClient,
                    showError = { message ->
                        android.widget.Toast
                            .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                            .show()
                    },
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
                onPairFromPhone = { screen = Screen.Pairing },
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
        Screen.Pairing -> {
            val pairing: PairingViewModel = viewModel(
                factory = PairingViewModel.Factory(
                    secureKeyStore = app.secureKeyStore,
                ),
            )
            PairingScreen(
                viewModel = pairing,
                onSuccess = { screen = Screen.Onboarding },
                onCancel = { screen = Screen.Onboarding },
            )
        }
    }
}
