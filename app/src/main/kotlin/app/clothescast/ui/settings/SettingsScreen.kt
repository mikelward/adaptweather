package app.clothescast.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clothescast.R

/**
 * One sub-page per concern. Order in the enum matches the order shown in the
 * root list: schedule + wardrobe come first (the most-frequently-tweaked rules),
 * voice + units after (set once, mostly forgotten), API keys / data sources /
 * about at the bottom.
 *
 * Public so callers (e.g. the onboarding flow) can deep-link into a specific
 * sub-page via [SettingsScreen]'s `initialRoute` param.
 */
enum class SettingsRoute(@StringRes val titleRes: Int) {
    Root(R.string.settings_title),
    Schedule(R.string.settings_root_schedule),
    Wardrobe(R.string.settings_root_wardrobe),
    Voice(R.string.settings_root_voice),
    Units(R.string.settings_root_units),
    ApiKeys(R.string.settings_root_api_keys),
    DataSources(R.string.settings_root_data_sources),
    About(R.string.settings_root_about),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    initialRoute: SettingsRoute? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(initialRoute ?: SettingsRoute.Root) }

    // When entered via deep link (initialRoute is non-null), back from the deep-linked
    // sub-page exits Settings entirely instead of going to Root — so onboarding's
    // "Continue → Schedule" needs only one back to reach Today, not two.
    fun goBackOrUp() {
        when {
            route == SettingsRoute.Root -> onNavigateBack()
            initialRoute != null && route == initialRoute -> onNavigateBack()
            else -> route = SettingsRoute.Root
        }
    }

    BackHandler(enabled = route != SettingsRoute.Root || initialRoute != null) {
        goBackOrUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(route.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = ::goBackOrUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (route) {
            SettingsRoute.Root -> SettingsRoot(
                padding = padding,
                onNavigate = { route = it },
            )
            SettingsRoute.Schedule -> ScheduleContent(
                time = state.scheduleTime,
                days = state.scheduleDays,
                tonightTime = state.tonightTime,
                tonightDays = state.tonightDays,
                tonightEnabled = state.tonightEnabled,
                deliveryMode = state.deliveryMode,
                padding = padding,
                onSetSchedule = viewModel::setSchedule,
                onSetTonightSchedule = viewModel::setTonightSchedule,
                onSetTonightEnabled = viewModel::setTonightEnabled,
                onSetDeliveryMode = viewModel::setDeliveryMode,
                // Show a Done button only when this page is the deep-link
                // landing from onboarding's "Continue" — gives the user an
                // obvious way to finish setup and reach Today. In the regular
                // settings flow they exit via the top-bar back arrow.
                onDone = if (initialRoute == SettingsRoute.Schedule) onNavigateBack else null,
            )
            SettingsRoute.Wardrobe -> WardrobeContent(
                rules = state.wardrobeRules,
                region = state.region,
                padding = padding,
                onAdd = viewModel::addWardrobeRule,
                onReplace = viewModel::replaceWardrobeRule,
                onDelete = viewModel::deleteWardrobeRule,
                onRegionChange = viewModel::setRegion,
            )
            SettingsRoute.Voice -> VoiceContent(
                selected = state.ttsEngine,
                geminiVoice = state.geminiVoice,
                openAiVoice = state.openAiVoice,
                elevenLabsVoice = state.elevenLabsVoice,
                geminiKeyConfigured = state.apiKeyConfigured,
                openAiKeyConfigured = state.openAiKeyConfigured,
                elevenLabsKeyConfigured = state.elevenLabsKeyConfigured,
                voiceLocale = state.voiceLocale,
                padding = padding,
                onSetTtsEngine = viewModel::setTtsEngine,
                onSetGeminiVoice = viewModel::setGeminiVoice,
                onSetOpenAiVoice = viewModel::setOpenAiVoice,
                onSetElevenLabsVoice = viewModel::setElevenLabsVoice,
                onSetVoiceLocale = viewModel::setVoiceLocale,
            )
            SettingsRoute.Units -> UnitsContent(
                temperatureUnit = state.temperatureUnit,
                distanceUnit = state.distanceUnit,
                padding = padding,
                onSetTemperatureUnit = viewModel::setTemperatureUnit,
                onSetDistanceUnit = viewModel::setDistanceUnit,
            )
            SettingsRoute.ApiKeys -> ApiKeysContent(
                geminiConfigured = state.apiKeyConfigured,
                openAiConfigured = state.openAiKeyConfigured,
                elevenLabsConfigured = state.elevenLabsKeyConfigured,
                padding = padding,
                onSetGeminiKey = viewModel::setApiKey,
                onClearGeminiKey = viewModel::clearApiKey,
                onSetOpenAiKey = viewModel::setOpenAiKey,
                onClearOpenAiKey = viewModel::clearOpenAiKey,
                onSetElevenLabsKey = viewModel::setElevenLabsKey,
                onClearElevenLabsKey = viewModel::clearElevenLabsKey,
            )
            SettingsRoute.DataSources -> DataSourcesContent(
                location = state.location,
                useDeviceLocation = state.useDeviceLocation,
                useCalendarEvents = state.useCalendarEvents,
                padding = padding,
                onSetUseDeviceLocation = viewModel::setUseDeviceLocation,
                onSelectLocation = viewModel::selectLocation,
                onClearLocation = viewModel::clearLocation,
                onSearchLocations = viewModel::searchLocations,
                onSetUseCalendarEvents = viewModel::setUseCalendarEvents,
            )
            SettingsRoute.About -> AboutContent(padding = padding)
        }
    }
}

@Composable
private fun SettingsRoot(
    padding: PaddingValues,
    onNavigate: (SettingsRoute) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationPermissionBanner()
        SettingsRoute.entries
            .filter { it != SettingsRoute.Root }
            .forEach { destination ->
                SettingsNavRow(
                    title = stringResource(destination.titleRes),
                    onClick = { onNavigate(destination) },
                )
            }
    }
}
