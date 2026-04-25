package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.WardrobeRule
import com.adaptweather.core.domain.model.WeatherCondition
import com.adaptweather.core.domain.model.symbol
import com.adaptweather.core.domain.model.toUnit
import java.time.LocalTime
import java.util.Locale

/**
 * The prompt sent to Gemini.
 *
 * Phrasing follows a deterministic rule set so the daily notification is always brief
 * and information-dense. The LLM only fires when at least one rule has something to
 * say; if every rule yields nothing, the worker skips the notification entirely.
 *
 * Rules (each yields 0 or 1 sentence):
 * 1. **Temperature**: if max OR min feels-like differs from yesterday by ≥ 3°C, output
 *    "It will be N° warmer/cooler today." (using the larger delta).
 * 2. **Wardrobe**: if today's triggered items differ from yesterday's, output
 *    "Wear <items>."
 * 3. **Precipitation**: if today's peak chance is ≥ 30%, output "<Type> at <HH:MM>."
 *
 * Calendar-event filtering and the 7pm out-of-hours briefing are not yet wired up.
 */
data class Prompt(
    val systemInstruction: String,
    val userMessage: String,
)

class BuildPrompt {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        yesterdayTriggeredRules: List<WardrobeRule>,
        todayTriggeredRules: List<WardrobeRule>,
        temperatureUnit: TemperatureUnit,
        languageTag: String,
    ): Prompt {
        val system = systemInstruction(languageTag)
        val user = buildUserMessage(
            today = today,
            yesterday = yesterday,
            yesterdayItems = yesterdayTriggeredRules.map { it.item },
            todayItems = todayTriggeredRules.map { it.item },
            temperatureUnit = temperatureUnit,
        )
        return Prompt(systemInstruction = system, userMessage = user)
    }

    private fun systemInstruction(languageTag: String): String = """
        You generate the body of a brief morning weather notification.

        All temperatures provided to you are pre-computed "feels like" (apparent) values.

        Apply these three rules in order. Each rule yields zero or one sentence.

        1. Temperature change: if today's high differs from yesterday's by at least 3°,
           OR today's low differs from yesterday's by at least 3°, output exactly
           "It will be N° warmer today." or "It will be N° cooler today.", using the
           larger absolute delta rounded to the nearest integer. Otherwise omit.
        2. Wardrobe change: today's triggered items and yesterday's are listed below.
           If they differ, output exactly "Wear <items>." with items separated by commas
           (Oxford comma for three or more). Otherwise omit.
        3. Precipitation: if today's peak precipitation chance is at least 30%, output
           exactly "<Type> at <HH:MM>." (e.g. "Rain at 15:00."). Otherwise omit.

        Concatenate the resulting sentences with a single space between each.
        No labels, no quotes, no preamble, no greetings, no emojis.
        If every rule produced nothing, output an empty string with no whitespace.
        Output language: $languageTag.
    """.trimIndent()

    private fun buildUserMessage(
        today: DailyForecast,
        yesterday: DailyForecast,
        yesterdayItems: List<String>,
        todayItems: List<String>,
        temperatureUnit: TemperatureUnit,
    ): String = buildString {
        appendLine("Yesterday (${yesterday.date}):")
        appendLine("- feels-like high: ${tempStr(yesterday.feelsLikeMaxC, temperatureUnit)}")
        appendLine("- feels-like low: ${tempStr(yesterday.feelsLikeMinC, temperatureUnit)}")
        appendLine("- conditions: ${conditionStr(yesterday.condition)}")
        appendLine()
        appendLine("Today (${today.date}):")
        appendLine("- feels-like high: ${tempStr(today.feelsLikeMaxC, temperatureUnit)}")
        appendLine("- feels-like low: ${tempStr(today.feelsLikeMinC, temperatureUnit)}")
        appendLine("- conditions: ${conditionStr(today.condition)}")
        appendLine("- max precipitation chance: ${today.precipitationProbabilityMaxPct.toInt()}%")
        val peakHour = peakPrecipHour(today)
        if (peakHour != null) {
            appendLine("- precipitation type: ${conditionStr(peakHour.condition)}")
            appendLine("- precipitation peak hour: ${peakHour.time}")
        }
        appendLine()
        appendLine("Yesterday's wardrobe items: ${yesterdayItems.formatList()}")
        appendLine("Today's wardrobe items: ${todayItems.formatList()}")
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

    private fun tempStr(celsius: Double, unit: TemperatureUnit): String {
        val v = celsius.toUnit(unit)
        return "%.0f%s".format(Locale.ROOT, v, unit.symbol())
    }

    private fun conditionStr(condition: WeatherCondition): String =
        condition.name.lowercase().replace('_', ' ')

    private data class PeakHour(val time: LocalTime, val condition: WeatherCondition)
}
