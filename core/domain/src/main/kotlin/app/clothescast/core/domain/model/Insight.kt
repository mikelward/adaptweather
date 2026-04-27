package app.clothescast.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * The output of a daily generation pass: the structured [InsightSummary] (clauses
 * driven by the wardrobe + forecast rules) plus the deterministic list of items
 * that triggered. The rule-based item list is kept separate from [summary] so
 * callers can render it (e.g. icons) without going through the prose formatter.
 *
 * Prose rendering — both notification body and TTS — happens in the `:app` layer
 * via the Android-side formatter, which resolves region-localized vocab, sentence
 * templates, and time formatting from string resources. Keeping [Insight] free of
 * prose lets the same cached payload re-render under a different region setting
 * without re-fetching the forecast.
 */
data class Insight(
    val summary: InsightSummary,
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
    /**
     * The outfit for the period that follows [period] — tonight when this is a
     * TODAY insight, tomorrow's daytime when this is a TONIGHT insight. Null
     * when the underlying forecast didn't carry enough data (legacy fixtures,
     * old caches) — the home screen falls back to a single-card layout in that
     * case. Lets the home screen show "Today + Tonight" or "Tonight + Tomorrow"
     * side-by-side from a single cached insight.
     */
    val nextOutfit: OutfitSuggestion? = null,
    /**
     * Which slice of the day this insight is for. [ForecastPeriod.TODAY] is the
     * morning pass (covers 07:00–19:00); [ForecastPeriod.TONIGHT] is the evening
     * pass (covers 19:00–07:00). Defaults to TODAY so older cached insights from
     * before the tonight feature still deserialise as morning insights.
     */
    val period: ForecastPeriod = ForecastPeriod.TODAY,
    /**
     * True when at least one calendar event was found in the relevant window for
     * this insight (today's events for TODAY, tonight's events for TONIGHT). Used
     * by the tonight notifier to decide between a silent and a default-priority
     * notification — events present means "you might need to actually leave the
     * house tonight," which warrants a sound and TTS read-out.
     */
    val hasEvents: Boolean = false,
)
