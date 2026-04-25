package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.Insight
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.UserPreferences
import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.repository.WeatherRepository
import java.time.Clock

/**
 * The product. Fetches yesterday + today, evaluates wardrobe rules, builds the prompt,
 * asks the LLM for a short comparative sentence, and packages the result.
 *
 * The rule evaluation runs *before* the LLM call so the deterministic list of items
 * is preserved in the [Insight] regardless of LLM output quality.
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
    ): Insight {
        val bundle = weatherRepository.fetchForecast(location)
        val yesterdayTriggered = evaluateWardrobeRules(bundle.yesterday, prefs.wardrobeRules)
        val todayTriggered = evaluateWardrobeRules(bundle.today, prefs.wardrobeRules)
        val prompt = buildPrompt(
            today = bundle.today,
            yesterday = bundle.yesterday,
            yesterdayTriggeredRules = yesterdayTriggered,
            todayTriggeredRules = todayTriggered,
            temperatureUnit = prefs.temperatureUnit,
            languageTag = languageTag,
        )
        val summary = insightGenerator.generate(prompt).trim()
        return Insight(
            summary = summary,
            recommendedItems = todayTriggered.map { it.item },
            generatedAt = clock.instant(),
            forDate = bundle.today.date,
        )
    }
}
