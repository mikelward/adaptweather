package app.adaptweather.core.domain.model

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
    /**
     * Cross-model agreement at fetch time, when available. Null when the
     * multi-model confidence call failed or the implementation doesn't compute
     * it. UI should treat null as "unknown" rather than "high".
     */
    val confidence: ConfidenceInfo? = null,
    /**
     * The big-picture top + bottom shown as icons on the home screen. Null on
     * insights from older app versions deserialised from cache; the next worker
     * run repopulates it.
     */
    val outfit: OutfitSuggestion? = null,
) {
    /**
     * The text that gets spoken aloud. The summary already includes the wardrobe
     * sentence, so this is just the summary itself. Kept as a method so callers
     * (notification + TTS) have a single phrasing entry point if it ever needs to
     * diverge from the rendered summary again.
     */
    fun spokenText(): String = summary
}
