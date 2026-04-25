package com.adaptweather.ui.settings

import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.TemperatureUnit

/**
 * What [SettingsScreen] needs to render. Settings that are not yet user-editable
 * (schedule, wardrobe rules) are intentionally absent and will land in follow-up PRs.
 */
data class SettingsState(
    val deliveryMode: DeliveryMode = DeliveryMode.NOTIFICATION_ONLY,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val apiKeyConfigured: Boolean = false,
)
