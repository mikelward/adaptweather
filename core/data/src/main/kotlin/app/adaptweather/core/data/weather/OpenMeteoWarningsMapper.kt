package app.adaptweather.core.data.weather

import app.adaptweather.core.domain.model.AlertSeverity
import app.adaptweather.core.domain.model.WeatherAlert
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Maps [OpenMeteoWarningsResponse] into a list of [WeatherAlert]. Entries with an
 * unparseable `onset` / `expires` timestamp are dropped rather than failing the whole
 * batch — a single malformed alert shouldn't suppress the rest. Severity strings are
 * compared case-insensitively against the CAP ladder; anything else falls back to
 * [AlertSeverity.UNKNOWN] so it still reaches the prompt as a non-priority alert.
 */
internal object OpenMeteoWarningsMapper {
    fun toAlerts(response: OpenMeteoWarningsResponse): List<WeatherAlert> =
        response.warnings.mapNotNull { it.toAlert() }

    private fun WarningDto.toAlert(): WeatherAlert? {
        val onsetInstant = parseInstant(onset) ?: return null
        val expiresInstant = parseInstant(expires) ?: return null
        return WeatherAlert(
            event = event,
            severity = parseSeverity(severity),
            headline = headline,
            description = description,
            onset = onsetInstant,
            expires = expiresInstant,
        )
    }

    private fun parseInstant(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }

    // Locale.ROOT keeps casing language-invariant: under tr-TR, "MINOR".lowercase()
    // becomes "mınor" (dotless ı) and would slip through to UNKNOWN.
    private fun parseSeverity(raw: String?): AlertSeverity = when (raw?.lowercase(Locale.ROOT)) {
        "minor" -> AlertSeverity.MINOR
        "moderate" -> AlertSeverity.MODERATE
        "severe" -> AlertSeverity.SEVERE
        "extreme" -> AlertSeverity.EXTREME
        else -> AlertSeverity.UNKNOWN
    }
}
