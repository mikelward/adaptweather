package app.adaptweather.core.domain.usecase

import app.adaptweather.core.domain.model.AlertSeverity
import app.adaptweather.core.domain.model.DailyForecast
import app.adaptweather.core.domain.model.TemperatureUnit
import app.adaptweather.core.domain.model.WardrobeRule
import app.adaptweather.core.domain.model.WeatherAlert
import app.adaptweather.core.domain.model.WeatherCondition
import app.adaptweather.core.domain.model.symbol
import app.adaptweather.core.domain.model.toUnit
import java.time.LocalTime
import java.util.Locale

/**
 * The prompt sent to Gemini.
 *
 * Phrasing follows a deterministic rule set so the daily notification is always brief
 * and information-dense.
 *
 * Rules (each yields 0 or 1 sentence):
 * 1. **Severe-weather alert**: if any active alert is SEVERE or EXTREME, output
 *    "Alert: <event>." for the highest-severity one.
 * 2. **Temperature band**: classify today's feels-like low and high into bands
 *    (freezing / cold / cool / mild / warm / hot) and output either
 *    "Today will be <band>." or "Today will be <coldband> to <hotband>.".
 *    This sentence is always emitted.
 * 3. **Temperature change vs yesterday**: if max OR min feels-like differs from
 *    yesterday by ≥ 3°C, output "It will be N° warmer/cooler today." (using the
 *    larger delta). Comes after the band sentence so the band sets the absolute
 *    context first and the delta follows as the comparison.
 * 4. **Wardrobe**: if any wardrobe rule triggers today, output "Wear <items>.".
 *    Always emitted when the list is non-empty — no comparison against yesterday.
 * 5. **Precipitation**: if today's peak chance is ≥ 30%, output "<Type> at <HH:MM>.".
 */
data class Prompt(
    val systemInstruction: String,
    val userMessage: String,
)

class BuildPrompt {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        todayTriggeredRules: List<WardrobeRule>,
        temperatureUnit: TemperatureUnit,
        languageTag: String,
        alerts: List<WeatherAlert> = emptyList(),
    ): Prompt {
        val system = systemInstruction(languageTag)
        val user = buildUserMessage(
            today = today,
            yesterday = yesterday,
            todayItems = todayTriggeredRules.map { it.item },
            temperatureUnit = temperatureUnit,
            alerts = alerts,
        )
        return Prompt(systemInstruction = system, userMessage = user)
    }

    private fun systemInstruction(languageTag: String): String = """
        You generate the body of a brief morning weather notification.

        All temperatures provided to you are pre-computed "feels like" (apparent) values.

        Apply these five rules in order. Each rule yields zero or one sentence.

        1. Severe alert: if a severe-weather alert is listed below with severity SEVERE
           or EXTREME, output exactly "Alert: <event>." using the event name of the
           highest-severity alert (EXTREME outranks SEVERE; ties take the first listed).
           Otherwise omit. If multiple alerts share the top severity, mention only one.
        2. Temperature band: a band label (e.g. "cool") or a "<low> to <high>" range
           (e.g. "cold to mild") is provided below as "feels-like band". Output exactly
           "Today will be <band>." Always emit this sentence.
        3. Temperature change vs yesterday: if today's high differs from yesterday's by
           at least 3°, OR today's low differs from yesterday's by at least 3°, output
           exactly "It will be N° warmer today." or "It will be N° cooler today." using
           the larger absolute delta rounded to the nearest integer. Otherwise omit.
        4. Wardrobe: today's triggered wardrobe items are listed below. If the list is
           non-empty, output exactly "Wear <items>." with items separated by commas
           (Oxford comma for three or more). Always emit when the list is non-empty —
           do not compare against any previous day. Otherwise omit.
        5. Precipitation: if today's peak precipitation chance is at least 30%, output
           exactly "<Type> at <HH:MM>." (e.g. "Rain at 15:00."). Otherwise omit.

        Concatenate the resulting sentences with a single space between each.
        No labels, no quotes, no preamble, no greetings, no emojis.
        Output language: $languageTag.
    """.trimIndent()

    private fun buildUserMessage(
        today: DailyForecast,
        yesterday: DailyForecast,
        todayItems: List<String>,
        temperatureUnit: TemperatureUnit,
        alerts: List<WeatherAlert>,
    ): String = buildString {
        appendLine("Yesterday (${yesterday.date}):")
        appendLine("- feels-like high: ${tempStr(yesterday.feelsLikeMaxC, temperatureUnit)}")
        appendLine("- feels-like low: ${tempStr(yesterday.feelsLikeMinC, temperatureUnit)}")
        appendLine("- conditions: ${conditionStr(yesterday.condition)}")
        appendLine()
        appendLine("Today (${today.date}):")
        appendLine("- feels-like high: ${tempStr(today.feelsLikeMaxC, temperatureUnit)}")
        appendLine("- feels-like low: ${tempStr(today.feelsLikeMinC, temperatureUnit)}")
        appendLine("- feels-like band: ${bandRangeLabel(today)}")
        appendLine("- conditions: ${conditionStr(today.condition)}")
        appendLine("- max precipitation chance: ${today.precipitationProbabilityMaxPct.toInt()}%")
        val peakHour = peakPrecipHour(today)
        if (peakHour != null) {
            appendLine("- precipitation type: ${conditionStr(peakHour.condition)}")
            appendLine("- precipitation peak hour: ${peakHour.time}")
        }
        appendLine()
        appendLine("Today's wardrobe items: ${todayItems.formatList()}")
        appendLine()
        appendLine("Severe-weather alerts: ${alertsBlock(alerts)}")
    }

    private fun bandRangeLabel(today: DailyForecast): String {
        val low = TemperatureBand.forCelsius(today.feelsLikeMinC)
        val high = TemperatureBand.forCelsius(today.feelsLikeMaxC)
        return if (low == high) low.label else "${low.label} to ${high.label}"
    }

    /**
     * The hour with the highest precipitation chance, only if that chance is ≥ 30%.
     * Used to feed the LLM both the type and the time so it can emit the precipitation
     * sentence. Falls back to the daily condition when no hourly entry qualifies.
     */
    private fun peakPrecipHour(today: DailyForecast): PeakHour? {
        val candidate = today.hourly.maxByOrNull { it.precipitationProbabilityPct }
        if (candidate == null || candidate.precipitationProbabilityPct < 30.0) {
            return if (today.precipitationProbabilityMaxPct >= 30.0) {
                PeakHour(time = LocalTime.NOON, condition = today.condition)
            } else {
                null
            }
        }
        val condition = if (candidate.condition == WeatherCondition.UNKNOWN) today.condition else candidate.condition
        return PeakHour(time = candidate.time, condition = condition)
    }

    private fun List<String>.formatList(): String = if (isEmpty()) "(none)" else joinToString(", ")

    private fun alertsBlock(alerts: List<WeatherAlert>): String {
        if (alerts.isEmpty()) return "(none)"
        return alerts.joinToString(separator = "; ") { alert ->
            "${alert.event} [${alert.severity.label()}]"
        }
    }

    private fun AlertSeverity.label(): String =
        name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun tempStr(celsius: Double, unit: TemperatureUnit): String {
        val v = celsius.toUnit(unit)
        return "%.0f%s".format(Locale.ROOT, v, unit.symbol())
    }

    private fun conditionStr(condition: WeatherCondition): String =
        condition.name.lowercase().replace('_', ' ')

    private data class PeakHour(val time: LocalTime, val condition: WeatherCondition)
}

/**
 * Bands classify a feels-like temperature (°C) into a glanceable label. Boundaries are
 * inclusive at the lower edge: 12.0°C is "cool", 11.9°C is "cold". Used to phrase the
 * temperature-band sentence in the daily insight as a single label or a low-to-high
 * range.
 */
internal enum class TemperatureBand(val label: String) {
    FREEZING("freezing"),
    COLD("cold"),
    COOL("cool"),
    MILD("mild"),
    WARM("warm"),
    HOT("hot");

    companion object {
        fun forCelsius(c: Double): TemperatureBand = when {
            c < 4.0 -> FREEZING
            c < 12.0 -> COLD
            c < 18.0 -> COOL
            c < 24.0 -> MILD
            c < 28.0 -> WARM
            else -> HOT
        }
    }
}
