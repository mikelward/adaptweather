package app.clothescast.core.domain.model

/**
 * A user-configured rule that suggests an item of clothing or gear when a forecast
 * crosses a threshold.
 *
 * Temperature thresholds are checked against "feels like" (apparent) values rather
 * than raw 2 m air temperature — what the user actually experiences when stepping
 * outside, factoring in wind chill and humidity. Precipitation rules check the day's
 * peak probability.
 */
data class WardrobeRule(
    val item: String,
    val condition: Condition,
) {
    fun appliesTo(forecast: DailyForecast): Boolean = condition.matches(forecast)

    sealed interface Condition {
        fun matches(forecast: DailyForecast): Boolean
    }

    data class TemperatureBelow(val celsius: Double) : Condition {
        override fun matches(forecast: DailyForecast) = forecast.feelsLikeMinC < celsius
    }

    data class TemperatureAbove(val celsius: Double) : Condition {
        override fun matches(forecast: DailyForecast) = forecast.feelsLikeMaxC > celsius
    }

    data class PrecipitationProbabilityAbove(val percent: Double) : Condition {
        override fun matches(forecast: DailyForecast) = forecast.precipitationProbabilityMaxPct > percent
    }

    companion object {
        /**
         * Item names use the **US English baseline** as canonical keys
         * ("sweater", not "jumper"). The `:app`-side formatter resolves each
         * key against region-localized string resources so a UK or AU user
         * sees "jumper" without these defaults changing.
         *
         * Free-form items added by the user (anything not in this list) are
         * rendered verbatim — no resource lookup, no localization attempt.
         */
        val DEFAULTS: List<WardrobeRule> = listOf(
            WardrobeRule("sweater", TemperatureBelow(18.0)),
            WardrobeRule("jacket", TemperatureBelow(12.0)),
            WardrobeRule("shorts", TemperatureAbove(24.0)),
            WardrobeRule("umbrella", PrecipitationProbabilityAbove(50.0)),
        )

        /**
         * The pre-region-localization default set, frozen at "jumper". Only
         * referenced by the migration path: if a stored rule list exactly
         * matches this set, it's a user who never customized their defaults
         * before the localization landed, and we silently swap them onto
         * [DEFAULTS] (US-baseline keys) so the formatter can localize.
         */
        val LEGACY_JUMPER_DEFAULTS: List<WardrobeRule> = listOf(
            WardrobeRule("jumper", TemperatureBelow(18.0)),
            WardrobeRule("jacket", TemperatureBelow(12.0)),
            WardrobeRule("shorts", TemperatureAbove(24.0)),
            WardrobeRule("umbrella", PrecipitationProbabilityAbove(50.0)),
        )
    }
}
