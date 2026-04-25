package com.adaptweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adaptweather.ui.settings.SettingsScreen
import com.adaptweather.ui.settings.SettingsViewModel
import com.adaptweather.ui.theme.AdaptWeatherTheme

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
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            settingsRepository = app.settingsRepository,
                            keyStore = app.secureKeyStore,
                            rearmAlarm = app.dailyAlarmScheduler::schedule,
                            geocodingClient = app.geocodingClient,
                        ),
                    )
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
