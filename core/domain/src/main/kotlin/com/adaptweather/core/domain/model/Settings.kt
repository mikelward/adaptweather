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
    /**
     * The fixed location to fetch weather for when [useDeviceLocation] is false (or as a
     * fallback when device location can't be resolved). Null when the user has not
     * configured one.
     */
    val location: Location? = null,
    /**
     * When true, the worker tries to read the device's coarse location at notify time
     * (network provider — no GPS hardware fix needed) and falls back to [location] /
     * platform default if the read fails or permission is not granted.
     */
    val useDeviceLocation: Boolean = false,
)
