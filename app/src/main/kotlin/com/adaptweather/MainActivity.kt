package com.adaptweather

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.adaptweather.ui.settings.SettingsScreen
import com.adaptweather.ui.settings.SettingsViewModel
import com.adaptweather.ui.theme.AdaptWeatherTheme
import com.adaptweather.ui.today.TodayScreen
import com.adaptweather.ui.today.TodayViewModel

private enum class Screen { Today, Settings }

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
    // Two-screen state machine. Compose Navigation would be overkill for this; the
    // back stack is at most one entry deep.
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }

    BackHandler(enabled = screen == Screen.Settings) {
        screen = Screen.Today
    }

    when (screen) {
        Screen.Today -> {
            val today: TodayViewModel = viewModel(
                factory = TodayViewModel.Factory(
                    insightCache = app.insightCache,
                    workManager = WorkManager.getInstance(app),
                ),
            )
            TodayScreen(
                viewModel = today,
                onNavigateToSettings = { screen = Screen.Settings },
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
                onNavigateBack = { screen = Screen.Today },
            )
        }
    }
}
