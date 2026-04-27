package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DailyForecast

/**
 * Filters a list of [ClothesRule]s down to those triggered by [forecast]. Order is
 * preserved so callers control presentation order via the input list.
 */
class EvaluateClothesRules {
    operator fun invoke(forecast: DailyForecast, rules: List<ClothesRule>): List<ClothesRule> =
        rules.filter { it.appliesTo(forecast) }
}
