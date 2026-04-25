package com.adaptweather.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * The output of a daily generation pass: a short readable summary suitable for both
 * a notification body and TTS, plus the deterministic list of items the wardrobe rules
 * triggered. The rule-based list is intentionally separate from [summary] so callers
 * can render it without re-parsing LLM text.
 */
data class Insight(
    val summary: String,
    val recommendedItems: List<String>,
    val generatedAt: Instant,
    val forDate: LocalDate,
    val hourly: List<HourlyForecast> = emptyList(),
) {
    /**
     * The text that gets spoken aloud. The LLM is told to weave the items into its
     * sentence but it's not guaranteed; appending them explicitly ensures the listener
     * always hears what their wardrobe rules suggest, even if the summary skips them.
     */
    fun spokenText(): String {
        if (recommendedItems.isEmpty()) return summary
        val joined = when (recommendedItems.size) {
            1 -> recommendedItems[0]
            2 -> "${recommendedItems[0]} and ${recommendedItems[1]}"
            else -> recommendedItems.dropLast(1).joinToString(", ") + ", and " + recommendedItems.last()
        }
        val separator = if (summary.endsWith(".") || summary.endsWith("!") || summary.endsWith("?")) " " else ". "
        return "$summary${separator}Recommended: $joined."
    }
}
