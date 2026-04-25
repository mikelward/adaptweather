package com.adaptweather.ui.settings

import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.Schedule
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.TtsEngine
import com.adaptweather.core.domain.model.WardrobeRule
import java.time.DayOfWeek
import java.time.LocalTime

/** What [SettingsScreen] needs to render. */
data class SettingsState(
    val scheduleTime: LocalTime = LocalTime.of(7, 0),
    val scheduleDays: Set<DayOfWeek> = Schedule.EVERY_DAY,
    val deliveryMode: DeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val wardrobeRules: List<WardrobeRule> = WardrobeRule.DEFAULTS,
    val location: Location? = null,
    val useDeviceLocation: Boolean = false,
    val ttsEngine: TtsEngine = TtsEngine.DEVICE,
    val apiKeyConfigured: Boolean = false,
)
