package app.clothescast.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.ui.theme.ClothesCastTheme
import java.time.LocalTime

//
// Preview wrappers for the Settings screens. Same pattern as `TodayPreviews.kt`:
// each `@Preview internal fun` is rendered both in Studio's design pane and in
// the Roborazzi snapshot test in `app/src/test`.
//
// One preview per sub-page in its default state. The settings sub-pages don't
// have many distinct visual states worth capturing (most variation is text
// copy and pickers nested in dialogs), so the snapshots here are first and
// foremost a layout-regression net for the cards / rows / FlowRow chip wraps.
//

@Composable
private fun SettingsFrame(content: @Composable () -> Unit) {
    ClothesCastTheme(dynamicColor = false) {
        Surface { content() }
    }
}

@Preview(name = "Settings · root list", widthDp = 360)
@Composable
internal fun SettingsRootPreview() {
    SettingsFrame {
        SettingsRoot(padding = PaddingValues(0.dp), onNavigate = {})
    }
}

@Preview(name = "Settings · Schedule", widthDp = 360)
@Composable
internal fun SettingsSchedulePreview() {
    SettingsFrame {
        ScheduleContent(
            time = LocalTime.of(7, 0),
            days = Schedule.EVERY_DAY,
            tonightTime = LocalTime.of(19, 0),
            tonightDays = Schedule.EVERY_DAY,
            tonightEnabled = true,
            tonightNotifyOnlyOnEvents = false,
            dailyMentionEveningEvents = false,
            deliveryMode = DeliveryMode.NOTIFICATION_ONLY,
            tonightDeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
            padding = PaddingValues(0.dp),
            onSetSchedule = { _, _ -> },
            onSetTonightSchedule = { _, _ -> },
            onSetTonightEnabled = {},
            onSetTonightNotifyOnlyOnEvents = {},
            onSetDailyMentionEveningEvents = {},
            onSetDeliveryMode = {},
            onSetTonightDeliveryMode = {},
        )
    }
}

@Preview(name = "Settings · Clothes rules", widthDp = 360)
@Composable
internal fun SettingsClothesPreview() {
    SettingsFrame {
        ClothesContent(
            rules = ClothesRule.DEFAULTS,
            padding = PaddingValues(0.dp),
            onAdd = {},
            onReplace = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(name = "Settings · Region & Units", widthDp = 360)
@Composable
internal fun SettingsRegionPreview() {
    SettingsFrame {
        RegionContent(
            region = Region.SYSTEM,
            temperatureUnit = TemperatureUnit.CELSIUS,
            distanceUnit = DistanceUnit.KILOMETERS,
            padding = PaddingValues(0.dp),
            onSetRegion = {},
            onSetTemperatureUnit = {},
            onSetDistanceUnit = {},
        )
    }
}

@Preview(name = "Settings · Voice (device engine)", widthDp = 360)
@Composable
internal fun SettingsVoiceDevicePreview() {
    SettingsFrame {
        VoiceContent(
            selected = TtsEngine.DEVICE,
            geminiVoice = UserPreferences.DEFAULT_GEMINI_VOICE,
            openAiVoice = UserPreferences.DEFAULT_OPENAI_VOICE,
            elevenLabsVoice = UserPreferences.DEFAULT_ELEVENLABS_VOICE,
            deviceVoice = null,
            deviceVoices = emptyList(),
            effectiveDeviceVoice = null,
            geminiKeyConfigured = false,
            openAiKeyConfigured = false,
            elevenLabsKeyConfigured = false,
            elevenLabsRefreshedVoices = null,
            elevenLabsRefreshing = false,
            elevenLabsModel = UserPreferences.DEFAULT_ELEVENLABS_MODEL,
            elevenLabsSpeed = UserPreferences.DEFAULT_ELEVENLABS_SPEED,
            elevenLabsStability = UserPreferences.DEFAULT_ELEVENLABS_STABILITY,
            openAiSpeed = UserPreferences.DEFAULT_OPENAI_SPEED,
            voiceLocale = VoiceLocale.SYSTEM,
            region = Region.SYSTEM,
            padding = PaddingValues(0.dp),
            onSetTtsEngine = {},
            onSetGeminiVoice = {},
            onSetOpenAiVoice = {},
            onSetOpenAiSpeed = {},
            onSetElevenLabsVoice = {},
            onSetDeviceVoice = {},
            onSetVoiceLocale = {},
            onSetGeminiKey = {},
            onClearGeminiKey = {},
            onSetOpenAiKey = {},
            onClearOpenAiKey = {},
            onSetElevenLabsKey = {},
            onClearElevenLabsKey = {},
            onRefreshElevenLabsVoices = {},
            onSetElevenLabsModel = {},
            onSetElevenLabsSpeed = {},
            onSetElevenLabsStability = {},
        )
    }
}

@Preview(name = "Settings · Data sources", widthDp = 360)
@Composable
internal fun SettingsDataSourcesPreview() {
    SettingsFrame {
        DataSourcesContent(
            location = null,
            useDeviceLocation = false,
            useCalendarEvents = false,
            padding = PaddingValues(0.dp),
            onSetUseDeviceLocation = {},
            onSelectLocation = {},
            onClearLocation = {},
            onSearchLocations = { emptyList() },
            onSetUseCalendarEvents = {},
        )
    }
}

// AboutContent intentionally not previewed: it reads BuildConfig.VERSION_NAME
// and VERSION_CODE, both derived from `git rev-list --count` + short SHA, so a
// snapshot would re-record on every commit and drown PR diffs in unrelated
// version-string churn. The screen is mostly static text + link buttons —
// low layout-regression risk to begin with.
