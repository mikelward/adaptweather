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
data class ClothesRule(
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
        val DEFAULTS: List<ClothesRule> = listOf(
            ClothesRule("jumper", TemperatureBelow(18.0)),
            ClothesRule("jacket", TemperatureBelow(12.0)),
            ClothesRule("shorts", TemperatureAbove(24.0)),
            ClothesRule("umbrella", PrecipitationProbabilityAbove(50.0)),
        )
    }
}
