package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.Insight
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.UserPreferences
import com.adaptweather.core.domain.model.WeatherAlert
import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.repository.WeatherRepository
import java.time.Clock

/**
 * The product. Fetches yesterday + today, evaluates wardrobe rules, builds the prompt,
 * asks the LLM for a short comparative sentence, and packages the result.
 *
 * The rule evaluation runs *before* the LLM call so the deterministic list of items
 * is preserved in the [Insight] regardless of LLM output quality.
 *
 * Severe-weather alerts piggy-back on the same fetch and are returned alongside the
 * insight in [DailyInsightResult]; the worker uses them to drive a separate
 * high-priority notification while still feeding them into the daily summary.
 */
class GenerateDailyInsight(
    private val weatherRepository: WeatherRepository,
    private val insightGenerator: InsightGenerator,
    private val evaluateWardrobeRules: EvaluateWardrobeRules = EvaluateWardrobeRules(),
    private val buildPrompt: BuildPrompt = BuildPrompt(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(
        location: Location,
        prefs: UserPreferences,
        languageTag: String,
    ): DailyInsightResult {
        val bundle = weatherRepository.fetchForecast(location)
        val activeAlerts = bundle.alerts.filter { it.expires.isAfter(clock.instant()) }
        val yesterdayTriggered = evaluateWardrobeRules(bundle.yesterday, prefs.wardrobeRules)
        val todayTriggered = evaluateWardrobeRules(bundle.today, prefs.wardrobeRules)
        val prompt = buildPrompt(
            today = bundle.today,
            yesterday = bundle.yesterday,
            yesterdayTriggeredRules = yesterdayTriggered,
            todayTriggeredRules = todayTriggered,
            temperatureUnit = prefs.temperatureUnit,
            languageTag = languageTag,
            alerts = activeAlerts,
        )
        val summary = insightGenerator.generate(prompt).trim()
        val insight = Insight(
            summary = summary,
            recommendedItems = todayTriggered.map { it.item },
            generatedAt = clock.instant(),
            forDate = bundle.today.date,
            hourly = bundle.today.hourly,
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
