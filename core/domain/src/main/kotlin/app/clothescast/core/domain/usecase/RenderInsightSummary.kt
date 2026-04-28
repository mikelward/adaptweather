package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.AlertSeverity
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarEvent
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.EveningEventTieInClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WeatherAlert
import app.clothescast.core.domain.model.WeatherCondition
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the structured [InsightSummary] for a daily generation pass: up to seven
 * independent clauses, each driven by an independent rule. The presentation layer
 * (an Android-side formatter) turns this into the final prose, so anything
 * region- or locale-specific (clothes vocab, sentence templates, time formatting)
 * is resolved there rather than here.
 *
 * Rules (each yields 0 or 1 clause):
 * 1. [AlertClause] — highest-severity SEVERE/EXTREME alert. Extreme outranks Severe;
 *    ties take the first listed.
 * 2. [BandClause] — classify feels-like low and high into bands. Always emitted.
 * 3. [DeltaClause] — yesterday vs today; only emitted when the larger absolute
 *    feels-like delta is ≥ 3°C, and only for [ForecastPeriod.TODAY] (the morning
 *    pass already mentioned this comparison; the tonight pass shouldn't repeat it).
 * 4. [ClothesClause] — items triggered by the user's rule list, in rule order.
 * 5. [PrecipClause] — peak chance ≥ 30% (hourly peak preferred; falls back to a
 *    noon synthesis when only the day-level field crosses the threshold).
 * 6. [CalendarTieInClause] — when clothes + precip both fired AND a calendar
 *    event overlaps the precip peak hour. Picks "umbrella" when on the clothes
 *    list, otherwise the first triggered item, mirroring rule 4's ordering.
 *    **Only emitted on [ForecastPeriod.TONIGHT].** On TODAY the bare precip
 *    clause ("Rain at 3pm.") is enough — the listener already knows about
 *    their morning event, so chaining a tie-in just repeats what they heard.
 * 7. [EveningEventTieInClause] — the morning's heads-up about a cold/rainy
 *    evening event, paired with a clothes item drawn from the *evening*
 *    forecast slice. Only emits on [ForecastPeriod.TODAY], gated on the
 *    caller passing non-empty [eveningEvents] + [eveningTriggeredRules]
 *    (which the use case in turn gates on the user opting in via
 *    "Mention evening events").
 *
 * All temperature comparisons use feels-like values, matching the clothes rules.
 */
class RenderInsightSummary {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        todayTriggeredRules: List<ClothesRule>,
        alerts: List<WeatherAlert> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        period: ForecastPeriod = ForecastPeriod.TODAY,
        eveningEvents: List<CalendarEvent> = emptyList(),
        eveningTriggeredRules: List<ClothesRule> = emptyList(),
    ): InsightSummary {
        val items = todayTriggeredRules.map { it.item }
        val peak = peakPrecip(today)
        return InsightSummary(
            period = period,
            alert = alertClause(alerts),
            band = bandClause(today),
            delta = if (period == ForecastPeriod.TODAY) deltaClause(today, yesterday) else null,
            clothes = clothesClause(items),
            precip = peak?.let { PrecipClause(it.condition, it.time) },
            // Calendar tie-in only fires on TONIGHT — pairing the precip peak
            // with an event the listener hasn't started yet ("Bring an umbrella
            // for your 8pm dinner") is the case where it adds value. On TODAY
            // the listener already knows about the event their morning is
            // built around, so the bare precip clause ("Rain at 3pm.") is
            // enough; chaining "Bring an umbrella for your 3pm standup." after
            // it just repeats what the user already heard.
            calendarTieIn = if (period == ForecastPeriod.TONIGHT) calendarTieInClause(items, peak, events) else null,
            eveningEventTieIn = eveningEventTieInClause(period, eveningEvents, eveningTriggeredRules),
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

    private fun clothesClause(items: List<String>): ClothesClause? =
        if (items.isEmpty()) null else ClothesClause(items)

    /**
     * Resolves the precipitation peak hour the way the precip rule needs it. Lifted
     * out of the precip clause assembly so the calendar-tie-in rule can pair an
     * event window against the same time without re-running the logic and getting
     * out of sync.
     *
     * Returns null unless the resolved condition is *actual* precipitation
     * (drizzle / rain / snow / thunderstorm). A cloudy or foggy day with a
     * 30%+ "precip" probability isn't worth a clause — the user wants the
     * spoken summary to mention precipitation, not haziness.
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
        if (!condition.isPrecipitation()) return null
        return PeakPrecip(time, condition)
    }

    private fun WeatherCondition.isPrecipitation(): Boolean = when (this) {
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.SNOW,
        WeatherCondition.THUNDERSTORM -> true
        WeatherCondition.CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.CLOUDY,
        WeatherCondition.FOG,
        WeatherCondition.UNKNOWN -> false
    }

    private fun calendarTieInClause(
        items: List<String>,
        peak: PeakPrecip?,
        events: List<CalendarEvent>,
    ): CalendarTieInClause? {
        if (items.isEmpty() || peak == null || events.isEmpty()) return null
        val event = events.firstOrNull { it.overlaps(peak.time) } ?: return null
        // Prefer "umbrella" when the user has it on their list — that's the clothes
        // item the precip-peak overlap was actually motivated by. Otherwise just
        // take the first triggered item, mirroring rule 4's ordering.
        val item = items.firstOrNull { it.equals("umbrella", ignoreCase = true) } ?: items.first()
        return CalendarTieInClause(item = item, time = peak.time, title = event.title)
    }

    /**
     * Evening-event tie-in for the morning insight. Only emits on [ForecastPeriod.TODAY]
     * — the tonight pass already covers evening events via [calendarTieInClause].
     * Picks the first evening event with a known start time and pairs it with the
     * first clothes item triggered by the evening forecast slice (umbrella first if
     * present). Caller is responsible for filtering [eveningEvents] to actually-evening
     * events and for evaluating clothes rules against the evening forecast.
     */
    private fun eveningEventTieInClause(
        period: ForecastPeriod,
        eveningEvents: List<CalendarEvent>,
        eveningTriggeredRules: List<ClothesRule>,
    ): EveningEventTieInClause? {
        if (period != ForecastPeriod.TODAY) return null
        if (eveningEvents.isEmpty() || eveningTriggeredRules.isEmpty()) return null
        val event = eveningEvents.firstOrNull { !it.allDay } ?: return null
        val items = eveningTriggeredRules.map { it.item }
        val item = items.firstOrNull { it.equals("umbrella", ignoreCase = true) } ?: items.first()
        return EveningEventTieInClause(item = item, title = event.title)
    }

    private data class PeakPrecip(val time: LocalTime, val condition: WeatherCondition)
}
