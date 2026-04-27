package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.DAY_END_HOUR
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.Location
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.core.domain.model.WeatherAlert
import app.clothescast.core.domain.model.daytime
import app.clothescast.core.domain.model.evening
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
    ): DailyInsightResult {
        val bundle = weatherRepository.fetchForecast(location)
        val activeAlerts = bundle.alerts.filter { it.expires.isAfter(clock.instant()) }
        // Wardrobe and summary speak for the daytime window only; the evening slice
        // is reserved for "bring a jacket for your dinner"-style addenda that piggy-
        // back on calendar events with a location.
        val todayDay = bundle.today.daytime()
        val yesterdayDay = bundle.yesterday.daytime()
        val todayEvening = bundle.today.evening()
        val todayTriggered = evaluateWardrobeRules(todayDay, prefs.wardrobeRules)
        val eveningTriggered = evaluateWardrobeRules(todayEvening, prefs.wardrobeRules)
        // Calendar events are gated on both the opt-in pref AND a configured reader.
        // Failures (missing permission, provider crash) degrade to no events so a
        // misbehaving reader can never fail the insight pipeline; the rest of the
        // summary still renders. Capture the property as a local so the smart-cast
        // survives across the runCatching lambda boundary.
        val reader = calendarEventReader
        val events = if (prefs.useCalendarEvents && reader != null) {
            runCatching {
                reader.eventsForDay(bundle.today.date, prefs.schedule.zoneId)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // "Out tonight" = a real, non-all-day event with a location that runs past
        // 19:00. Location-as-a-string stands in for "not at home" until we have a
        // proper home-location setting. Tomorrow's small hours aren't queried yet
        // (eventsForDay is single-day), so a midnight–02:00 event next door is missed
        // for now — TODO: also fetch tomorrow's events and union the windows.
        val eveningEvents = events.filter { event ->
            !event.allDay &&
                !event.location.isNullOrBlank() &&
                event.end.isAfter(LocalTime.of(DAY_END_HOUR, 0))
        }
        val summary = renderInsightSummary(
            today = todayDay,
            yesterday = yesterdayDay,
            todayTriggeredRules = todayTriggered,
            alerts = activeAlerts,
            events = events,
            eveningTriggeredRules = eveningTriggered,
            eveningEvents = eveningEvents,
        )
        val insight = Insight(
            summary = summary,
            recommendedItems = todayTriggered.map { it.item },
            generatedAt = clock.instant(),
            forDate = bundle.today.date,
            hourly = bundle.today.hourly,
            confidence = bundle.confidence,
            outfit = OutfitSuggestion.fromForecast(todayDay),
        )
        return DailyInsightResult(insight = insight, alerts = activeAlerts)
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
