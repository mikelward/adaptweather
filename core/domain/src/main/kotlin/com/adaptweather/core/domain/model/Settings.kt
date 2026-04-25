package com.adaptweather.core.domain.model

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class DistanceUnit { KILOMETERS, MILES }

enum class DeliveryMode { NOTIFICATION_ONLY, TTS_ONLY, NOTIFICATION_AND_TTS }

data class UserPreferences(
    val schedule: Schedule,
    val deliveryMode: DeliveryMode,
    val temperatureUnit: TemperatureUnit,
    val distanceUnit: DistanceUnit,
    val wardrobeRules: List<WardrobeRule>,
)
