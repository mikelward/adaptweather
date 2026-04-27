package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.AlertSeverity
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarEvent
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.NextPeriodClause
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
import app.clothescast.core.domain.model.WardrobeRule
import app.clothescast.core.domain.model.WeatherAlert
import app.clothescast.core.domain.model.WeatherCondition
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the structured [InsightSummary] for a daily generation pass: up to six
 * independent clauses, each driven by an independent rule. The presentation layer
 * (an Android-side formatter) turns this into the final prose, so anything
 * region- or locale-specific (wardrobe vocab, sentence templates, time formatting)
 * is resolved there rather than here.
 *
 * Rules (each yields 0 or 1 clause):
 * 1. [AlertClause] — highest-severity SEVERE/EXTREME alert. Extreme outranks Severe;
 *    ties take the first listed.
 * 2. [BandClause] — classify feels-like low and high into bands. Always emitted.
 * 3. [DeltaClause] — yesterday vs today; only emitted when the larger absolute
 *    feels-like delta is ≥ 3°C, and only for [ForecastPeriod.TODAY] (the morning
 *    pass already mentioned this comparison; the tonight pass shouldn't repeat it).
 * 4. [WardrobeClause] — items triggered by the user's rule list, in rule order.
 * 5. [PrecipClause] — peak chance ≥ 30% (hourly peak preferred; falls back to a
 *    noon synthesis when only the day-level field crosses the threshold).
 * 6. [CalendarTieInClause] — when wardrobe + precip both fired AND a calendar
 *    event overlaps the precip peak hour. Picks "umbrella" when on the wardrobe
 *    list, otherwise the first triggered item, mirroring rule 4's ordering.
 * 7. [NextPeriodClause] — forward-looking heads-up about the *next* period
 *    (tonight, after a TODAY pass; tomorrow daytime, after a TONIGHT pass).
 *    Only emitted when the next period carries precip (≥30%) or is at least 3°C
 *    colder (feels-like min) than the current period — quiet, normal-cycle
 *    transitions stay silent.
 *
 * All temperature comparisons use feels-like values, matching the wardrobe rules.
 */
class RenderInsightSummary {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        todayTriggeredRules: List<WardrobeRule>,
        alerts: List<WeatherAlert> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        period: ForecastPeriod = ForecastPeriod.TODAY,
        next: DailyForecast? = null,
    ): InsightSummary {
        val items = todayTriggeredRules.map { it.item }
        val peak = peakPrecip(today)
        return InsightSummary(
            period = period,
            alert = alertClause(alerts),
            band = bandClause(today),
            delta = if (period == ForecastPeriod.TODAY) deltaClause(today, yesterday) else null,
            wardrobe = wardrobeClause(items),
            precip = peak?.let { PrecipClause(it.condition, it.time) },
            calendarTieIn = calendarTieInClause(items, peak, events),
            nextPeriod = nextPeriodClause(today, next),
        )
    }

    private fun alertClause(alerts: List<WeatherAlert>): AlertClause? {
        val top = alerts.firstOrNull { it.severity == AlertSeverity.EXTREME }
            ?: alerts.firstOrNull { it.severity == AlertSeverity.SEVERE }
            ?: return null
        return AlertClause(top.event)
    }

    private fun bandClause(today: DailyForecast): BandClause = BandClause(
        low = TemperatureBand.forCelsius(today.feelsLikeMinC),
        high = TemperatureBand.forCelsius(today.feelsLikeMaxC),
    )

    private fun deltaClause(today: DailyForecast, yesterday: DailyForecast): DeltaClause? {
        val highDelta = today.feelsLikeMaxC - yesterday.feelsLikeMaxC
        val lowDelta = today.feelsLikeMinC - yesterday.feelsLikeMinC
        val biggest = if (abs(highDelta) >= abs(lowDelta)) highDelta else lowDelta
        // Apply the threshold against the *unrounded* delta. Otherwise 2.6°C rounds
        // to 3 and would emit a clause even though the actual delta is under the
        // 3° rule.
        if (abs(biggest) < 3.0) return null
        val rounded = biggest.roundToInt()
        val direction = if (rounded > 0) DeltaClause.Direction.WARMER else DeltaClause.Direction.COOLER
        return DeltaClause(degrees = abs(rounded), direction = direction)
    }

    private fun wardrobeClause(items: List<String>): WardrobeClause? =
        if (items.isEmpty()) null else WardrobeClause(items)

    /**
     * Resolves the precipitation peak hour the way the precip rule needs it. Lifted
     * out of the precip clause assembly so the calendar-tie-in rule can pair an
     * event window against the same time without re-running the logic and getting
     * out of sync.
     */
    private fun peakPrecip(today: DailyForecast): PeakPrecip? {
        val peak = today.hourly.maxByOrNull { it.precipitationProbabilityPct }
        val time: LocalTime
        val condition: WeatherCondition
        if (peak == null || peak.precipitationProbabilityPct < 30.0) {
            if (today.precipitationProbabilityMaxPct < 30.0) return null
            time = LocalTime.NOON
            condition = today.condition
        } else {
            time = peak.time
            condition = if (peak.condition == WeatherCondition.UNKNOWN) today.condition else peak.condition
        }
        return PeakPrecip(time, condition)
    }

    private fun nextPeriodClause(
        current: DailyForecast,
        next: DailyForecast?,
    ): NextPeriodClause? {
        if (next == null) return null
        val nextPeak = peakPrecip(next)
        val precipClause = nextPeak?.let { PrecipClause(it.condition, it.time) }
        // ≥3°C feels-like-min drop, calibrated against the *current* period's min
        // (not yesterday or some absolute floor) so the clause fires on a real
        // cooling trend rather than the routine night-vs-day cycle. Same threshold
        // as the existing [DeltaClause] rule for consistency.
        val isColder = next.feelsLikeMinC <= current.feelsLikeMinC - 3.0
        if (precipClause == null && !isColder) return null
        return NextPeriodClause(precip = precipClause, isColder = isColder)
    }

    private fun calendarTieInClause(
        items: List<String>,
        peak: PeakPrecip?,
        events: List<CalendarEvent>,
    ): CalendarTieInClause? {
        if (items.isEmpty() || peak == null || events.isEmpty()) return null
        val event = events.firstOrNull { it.overlaps(peak.time) } ?: return null
        // Prefer "umbrella" when the user has it on their list — that's the wardrobe
        // item the precip-peak overlap was actually motivated by. Otherwise just
        // take the first triggered item, mirroring rule 4's ordering.
        val item = items.firstOrNull { it.equals("umbrella", ignoreCase = true) } ?: items.first()
        return CalendarTieInClause(item = item, time = peak.time, title = event.title)
    }

    private data class PeakPrecip(val time: LocalTime, val condition: WeatherCondition)
}
