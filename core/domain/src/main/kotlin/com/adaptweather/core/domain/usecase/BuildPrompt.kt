package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.WardrobeRule
import com.adaptweather.core.domain.model.symbol
import com.adaptweather.core.domain.model.toUnit
import java.util.Locale

/**
 * The prompt sent to Gemini. System instruction sets style; user message carries
 * the structured forecast comparison and the deterministic wardrobe-rule output.
 *
 * The triggered items are computed by [EvaluateWardrobeRules] and threaded into the
 * prompt as a hint — the LLM can weave them naturally — but callers also keep the
 * structured list in the [com.adaptweather.core.domain.model.Insight], so a flaky
 * LLM response never hides them from the UI.
 */
data class Prompt(
    val systemInstruction: String,
    val userMessage: String,
)

class BuildPrompt {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        triggeredRules: List<WardrobeRule>,
        temperatureUnit: TemperatureUnit,
        languageTag: String,
    ): Prompt {
        val system = buildString {
            append("Write one short sentence (max 25 words) in language ")
            append(languageTag)
            append(" comparing today's weather to yesterday's actual weather, and offer ")
            append("one piece of actionable advice if useful. ")
            append("Output the sentence only — no quotes, no preamble, no greetings, no emojis.")
        }

        val user = buildString {
            appendLine("Yesterday (${yesterday.date}):")
            appendLine("- min ${tempStr(yesterday.temperatureMinC, temperatureUnit)}, max ${tempStr(yesterday.temperatureMaxC, temperatureUnit)}")
            appendLine("- precipitation chance peak: ${yesterday.precipitationProbabilityMaxPct.toInt()}%")
            appendLine("- conditions: ${conditionStr(yesterday)}")
            appendLine()
            appendLine("Today (${today.date}):")
            appendLine("- min ${tempStr(today.temperatureMinC, temperatureUnit)}, max ${tempStr(today.temperatureMaxC, temperatureUnit)}")
            appendLine("- precipitation chance peak: ${today.precipitationProbabilityMaxPct.toInt()}%")
            appendLine("- conditions: ${conditionStr(today)}")

            val peakHour = today.hourly.maxByOrNull { it.precipitationProbabilityPct }
            if (peakHour != null && peakHour.precipitationProbabilityPct >= 30.0) {
                appendLine("- highest rain chance around ${peakHour.time} (${peakHour.precipitationProbabilityPct.toInt()}%)")
            }

            if (triggeredRules.isNotEmpty()) {
                appendLine()
                appendLine("Recommended items based on user thresholds: ${triggeredRules.joinToString(", ") { it.item }}")
            }

            appendLine()
            append("Compare yesterday and today, and advise.")
        }

        return Prompt(systemInstruction = system, userMessage = user)
    }

    private fun tempStr(celsius: Double, unit: TemperatureUnit): String {
        val v = celsius.toUnit(unit)
        return "%.0f%s".format(Locale.ROOT, v, unit.symbol())
    }

    private fun conditionStr(forecast: DailyForecast): String =
        forecast.condition.name.lowercase().replace('_', ' ')
}
