package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.WardrobeRule

/**
 * Filters a list of [WardrobeRule]s down to those triggered by [forecast]. Order is
 * preserved so callers control presentation order via the input list.
 */
class EvaluateWardrobeRules {
    operator fun invoke(forecast: DailyForecast, rules: List<WardrobeRule>): List<WardrobeRule> =
        rules.filter { it.appliesTo(forecast) }
}
