package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.CalendarEvent
import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.WeatherAlert
import app.clothescast.core.domain.model.WeatherCondition
import app.clothescast.core.domain.repository.CalendarEventReader
import app.clothescast.core.domain.repository.WeatherRepository
import java.time.Clock
import java.time.LocalTime

/**
 * The product. Fetches the forecast, evaluates wardrobe rules, renders the
 * deterministic summary string in [renderInsightSummary], and packages the result.
 *
 * Severe-weather alerts piggy-back on the same fetch and are returned alongside the
 * insight in [DailyInsightResult]; the worker uses them to drive a separate
 * high-priority notification while still feeding them into the daily summary.
 */
class GenerateDailyInsight(
    private val weatherRepository: WeatherRepository,
    private val evaluateWardrobeRules: EvaluateWardrobeRules = EvaluateWardrobeRules(),
    private val renderInsightSummary: RenderInsightSummary = RenderInsightSummary(),
    private val calendarEventReader: CalendarEventReader? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(
        location: Location,
        prefs: UserPreferences,
        period: ForecastPeriod = ForecastPeriod.TODAY,
    ): DailyInsightResult {
        val bundle = weatherRepository.fetchForecast(location)
        val activeAlerts = bundle.alerts.filter { it.expires.isAfter(clock.instant()) }
        // For TONIGHT, narrow the daily slice to the user-configured overnight
        // window: from the user's tonight time today, wrapping past midnight, up
        // to the user's morning time tomorrow. We thread `prefs.tonightSchedule.time`
        // and `prefs.schedule.time` (not fixed constants) so a user who moved
        // either dial sees an insight that matches the alarm that just fired.
        val tonightStart = prefs.tonightSchedule.time
        val morningEnd = prefs.schedule.time
        val periodForecast = when (period) {
            ForecastPeriod.TODAY -> bundle.today
            ForecastPeriod.TONIGHT -> bundle.today.slicedForTonight(
                tonightStart = tonightStart,
                morningEnd = morningEnd,
                tomorrowHourly = bundle.tomorrowHourly,
            )
        }
        val todayTriggered = evaluateWardrobeRules(periodForecast, prefs.wardrobeRules)
        // Calendar events are gated on both the opt-in pref AND a configured reader.
        // Failures (missing permission, provider crash) degrade to no events so a
        // misbehaving reader can never fail the insight pipeline; the rest of the
        // summary still renders. Capture the property as a local so the smart-cast
        // survives across the runCatching lambda boundary.
        val reader = calendarEventReader
        val allEvents = if (prefs.useCalendarEvents && reader != null) {
            runCatching {
                reader.eventsForDay(bundle.today.date, prefs.schedule.zoneId)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val periodEvents = filterEventsForPeriod(allEvents, period, tonightStart)
        val summary = renderInsightSummary(
            today = periodForecast,
            yesterday = bundle.yesterday,
            todayTriggeredRules = todayTriggered,
            alerts = activeAlerts,
            events = periodEvents,
            period = period,
        )
        val insight = Insight(
            summary = summary,
            recommendedItems = todayTriggered.map { it.item },
            generatedAt = clock.instant(),
            forDate = bundle.today.date,
            hourly = periodForecast.hourly,
            confidence = bundle.confidence,
            outfit = OutfitSuggestion.fromForecast(periodForecast),
            period = period,
            hasEvents = periodEvents.isNotEmpty(),
        )
        return DailyInsightResult(insight = insight, alerts = activeAlerts)
    }

    private fun filterEventsForPeriod(
        events: List<CalendarEvent>,
        period: ForecastPeriod,
        tonightStart: LocalTime,
    ): List<CalendarEvent> = when (period) {
        ForecastPeriod.TODAY -> events
        // Tonight = anything starting at or after the tonight time, OR an all-day
        // event that bleeds across the evening (treated as relevant context).
        ForecastPeriod.TONIGHT -> events.filter { it.allDay || !it.start.isBefore(tonightStart) }
    }
}

/**
 * Slices [DailyForecast] to the overnight tonight window (today at [tonightStart]
 * through tomorrow at [morningEnd]). Used by TONIGHT to keep the rendered insight
 * focused on what the user will walk into between bedtime and the morning.
 *
 * Concatenates today's hourly entries at or after [tonightStart] with tomorrow's
 * hourly entries strictly before [morningEnd]. Tomorrow entries keep their wall
 * time (e.g. 06:00) — [HourlyForecast] doesn't carry a date, but the precip-peak
 * sentence is fine with that since it talks about wall-clock times.
 *
 * Beyond just filtering, this also recomputes the daily-level aggregates
 * ([DailyForecast.feelsLikeMinC] etc.) from the combined hourly so that:
 *
 *  - [RenderInsightSummary]'s band sentence ("Tonight will be cold to mild.") talks
 *    about the actual overnight low — typically the pre-dawn hours from
 *    [tomorrowHourly], which the day-level fields don't capture.
 *  - The wardrobe rules evaluate against the overnight conditions (the user
 *    pulling a thicker jumper because it'll be 4°C at 05:00, not the day's 18°C
 *    midday high).
 *  - [OutfitSuggestion.fromForecast] picks a top + bottom appropriate for what
 *    the user will leave in tonight and arrive in tomorrow morning.
 *  - The precip-peak fallback (when the hourly peak < 30% and it falls back to
 *    the daily fields) doesn't surface "Rain at 12:00" on a tonight insight
 *    after the morning rain has already passed.
 *
 * If [tonightStart] isn't strictly before [morningEnd] (e.g. the user set their
 * tonight time to 03:00) we treat tonight as today-only, no wrap — anything else
 * would either double-count hours or invert the window.
 *
 * Falls back to the original [DailyForecast] when the combined hourly is empty
 * (older cached payloads, sparse fixtures, or a tonight time after the last
 * available hour) so we still emit *something* rather than a forecast with
 * all-zero aggregates.
 */
private fun DailyForecast.slicedForTonight(
    tonightStart: LocalTime,
    morningEnd: LocalTime,
    tomorrowHourly: List<HourlyForecast>,
): DailyForecast {
    val tonightHours = hourly.filter { it.time >= tonightStart }
    val tomorrowMorning = if (tonightStart.isBefore(morningEnd)) {
        // Same-day window (no real "evening"); skip the overnight wrap to avoid
        // double-counting hours with today.
        emptyList()
    } else {
        tomorrowHourly.filter { it.time < morningEnd }
    }
    val sliced = tonightHours + tomorrowMorning
    if (sliced.isEmpty()) return copy(hourly = sliced)
    return copy(
        hourly = sliced,
        temperatureMinC = sliced.minOf { it.temperatureC },
        temperatureMaxC = sliced.maxOf { it.temperatureC },
        feelsLikeMinC = sliced.minOf { it.feelsLikeC },
        feelsLikeMaxC = sliced.maxOf { it.feelsLikeC },
        precipitationProbabilityMaxPct = sliced.maxOf { it.precipitationProbabilityPct },
        // The day's overall condition (e.g. RAIN) may have applied to morning rain
        // that's now over. Use the condition from the wettest hour in the slice as
        // a better proxy for "what's it doing tonight"; fall back to the day-level
        // condition only if every overnight hour is UNKNOWN.
        condition = sliced.maxByOrNull { it.precipitationProbabilityPct }
            ?.condition
            ?.takeIf { it != WeatherCondition.UNKNOWN }
            ?: condition,
    )
}

/**
 * Bundles the daily insight with the active alerts that informed it. Alerts are kept
 * separate from [Insight] because they have a different lifecycle: the insight is
 * cached for the day and redelivered on demand, while alerts drive a one-shot
 * high-priority notification at fetch time.
 */
data class DailyInsightResult(
    val insight: Insight,
    val alerts: List<WeatherAlert>,
)
