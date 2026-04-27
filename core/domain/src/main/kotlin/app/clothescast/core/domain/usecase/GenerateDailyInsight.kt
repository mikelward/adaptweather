package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.CalendarEvent
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.WeatherAlert
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
        // For TONIGHT, narrow the daily slice to the overnight window. The bundle's
        // hourly already covers the full day; we just trim it to >=19:00 so the
        // chart and the precip-peak calculation talk about tonight, not the morning
        // hours the user already heard about.
        val source = bundle.today
        val periodForecast = when (period) {
            ForecastPeriod.TODAY -> source
            ForecastPeriod.TONIGHT -> source.copy(hourly = source.hourly.filter { it.time >= TONIGHT_START })
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
        val periodEvents = filterEventsForPeriod(allEvents, period)
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
    ): List<CalendarEvent> = when (period) {
        ForecastPeriod.TODAY -> events
        // Tonight = anything starting at or after 19:00 today, OR an all-day event
        // that bleeds across the evening (treated as relevant context).
        ForecastPeriod.TONIGHT -> events.filter { it.allDay || !it.start.isBefore(TONIGHT_START) }
    }

    private companion object {
        private val TONIGHT_START: LocalTime = LocalTime.of(19, 0)
    }
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
