package app.adaptweather.core.domain.usecase

import app.adaptweather.core.domain.model.Insight
import app.adaptweather.core.domain.model.Location
import app.adaptweather.core.domain.model.OutfitSuggestion
import app.adaptweather.core.domain.model.UserPreferences
import app.adaptweather.core.domain.model.WeatherAlert
import app.adaptweather.core.domain.repository.WeatherRepository
import java.time.Clock

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
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(
        location: Location,
        prefs: UserPreferences,
    ): DailyInsightResult {
        val bundle = weatherRepository.fetchForecast(location)
        val activeAlerts = bundle.alerts.filter { it.expires.isAfter(clock.instant()) }
        val todayTriggered = evaluateWardrobeRules(bundle.today, prefs.wardrobeRules)
        val summary = renderInsightSummary(
            today = bundle.today,
            yesterday = bundle.yesterday,
            todayTriggeredRules = todayTriggered,
            alerts = activeAlerts,
        )
        val insight = Insight(
            summary = summary,
            recommendedItems = todayTriggered.map { it.item },
            generatedAt = clock.instant(),
            forDate = bundle.today.date,
            hourly = bundle.today.hourly,
            confidence = bundle.confidence,
            outfit = OutfitSuggestion.fromForecast(bundle.today),
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
