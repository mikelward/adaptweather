package com.adaptweather.core.domain.model

import java.time.Instant

/**
 * One severe-weather alert as published by the upstream warnings feed.
 *
 * `event` is the headline alert type (e.g. "Severe Thunderstorm Warning"); `severity`
 * follows the Common Alerting Protocol levels. `onset` and `expires` define the valid
 * window — alerts whose `expires` is in the past are filtered out before consumers see
 * them. `headline` and `description` are free-form strings from the upstream provider
 * and may be null when the feed only sends an event type.
 */
data class WeatherAlert(
    val event: String,
    val severity: AlertSeverity,
    val headline: String?,
    val description: String?,
    val onset: Instant,
    val expires: Instant,
) {
    fun isHighPriority(): Boolean = severity.isHighPriority()
}

/** CAP-aligned severity ladder. SEVERE and EXTREME drive a separate notification. */
enum class AlertSeverity {
    MINOR,
    MODERATE,
    SEVERE,
    EXTREME,
    UNKNOWN;

    fun isHighPriority(): Boolean = this == SEVERE || this == EXTREME
}
