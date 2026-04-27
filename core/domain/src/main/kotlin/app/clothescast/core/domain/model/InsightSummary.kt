package app.clothescast.core.domain.model

import java.time.LocalTime

/**
 * The structured form of a daily insight. Each clause is a pure-data description of
 * one of the six independent rules in [app.clothescast.core.domain.usecase.RenderInsightSummary]
 * (alert, band, delta, wardrobe, precip, calendar tie-in); the corresponding prose
 * is produced by an Android-side formatter that resolves item keys and templates
 * through string resources.
 *
 * Splitting "what to say" (this) from "how to say it" (the formatter) lets us:
 *  - Localize wardrobe vocab per region without re-fetching the forecast — the
 *    same cached [InsightSummary] can re-render as "sweater" or "jumper" depending
 *    on the user's Region setting.
 *  - Keep [app.clothescast.core.domain.usecase.RenderInsightSummary] free of any
 *    Android dependency, so it stays pure-Kotlin testable on the JVM.
 *
 * The [period] determines a couple of presentation choices: the band sentence's
 * lead-in ("Today will be …" vs "Tonight will be …"), and whether the [delta]
 * clause is emitted at all (tonight skips the yesterday-vs-today comparison the
 * morning pass already covered).
 */
data class InsightSummary(
    val period: ForecastPeriod,
    val band: BandClause,
    val alert: AlertClause? = null,
    val delta: DeltaClause? = null,
    val wardrobe: WardrobeClause? = null,
    val precip: PrecipClause? = null,
    val calendarTieIn: CalendarTieInClause? = null,
)

/**
 * Highest-severity SEVERE/EXTREME alert event name (e.g. "Tornado Warning"). Pulled
 * verbatim from the upstream warning feed; the formatter renders it as
 * "Alert: <event>." with no further interpretation.
 */
data class AlertClause(val event: String)

/**
 * The feels-like temperature band(s) for the period. When [low] == [high] the
 * formatter emits a single label ("Today will be mild."); otherwise it emits a
 * range ("Today will be cool to mild.") in low-to-high order.
 */
data class BandClause(val low: TemperatureBand, val high: TemperatureBand)

/**
 * Yesterday-to-today feels-like delta, rounded to whole degrees. [degrees] is
 * always positive; [direction] carries the sign. Only emitted when the unrounded
 * larger-of-(min,max) delta is ≥ 3°C.
 */
data class DeltaClause(val degrees: Int, val direction: Direction) {
    enum class Direction { WARMER, COOLER }
}

/**
 * Wardrobe items that triggered this period, in user-rule order. Each [item] is
 * the verbatim rule key — typically a US-baseline noun ("sweater", "jacket",
 * "umbrella", "shorts") which the formatter looks up against `wardrobe_item_<key>`
 * string resources, falling through to the literal key for user-added rules with
 * no resource match.
 */
data class WardrobeClause(val items: List<String>)

/**
 * Peak precipitation hour: a [condition] (RAIN, SNOW, etc.) at a wall-clock [time].
 * The formatter capitalises and humanises the condition name ("Rain at 15:00.").
 */
data class PrecipClause(val condition: WeatherCondition, val time: LocalTime)

/**
 * Calendar tie-in: a wardrobe [item] paired with a calendar event that overlaps
 * the precipitation peak. The formatter renders this as "Bring <item> for your
 * <time> <title>." with article picking on [item].
 */
data class CalendarTieInClause(
    val item: String,
    val time: LocalTime,
    val title: String,
)

/**
 * Bands classify a feels-like temperature (°C) into a glanceable label. Boundaries
 * are inclusive at the lower edge: 12.0°C is COOL, 11.9°C is COLD.
 *
 * The enum names are presentation-neutral; the formatter maps each to a localized
 * `temperature_band_<name>` string resource.
 */
enum class TemperatureBand {
    FREEZING,
    COLD,
    COOL,
    MILD,
    WARM,
    HOT;

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
