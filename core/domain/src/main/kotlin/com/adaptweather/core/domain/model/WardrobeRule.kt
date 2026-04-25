package com.adaptweather.core.domain.model

/**
 * A user-configured rule that suggests an item of clothing or gear when a forecast
 * crosses a threshold. The semantics are deliberately simple: "below" rules check
 * the day's minimum temperature, "above" rules check the day's maximum, and precipitation
 * rules check the day's peak probability.
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
        override fun matches(forecast: DailyForecast) = forecast.temperatureMinC < celsius
    }

    data class TemperatureAbove(val celsius: Double) : Condition {
        override fun matches(forecast: DailyForecast) = forecast.temperatureMaxC > celsius
    }

    data class PrecipitationProbabilityAbove(val percent: Double) : Condition {
        override fun matches(forecast: DailyForecast) = forecast.precipitationProbabilityMaxPct > percent
    }

    companion object {
        val DEFAULTS: List<WardrobeRule> = listOf(
            WardrobeRule("jumper", TemperatureBelow(18.0)),
            WardrobeRule("jacket", TemperatureBelow(12.0)),
            WardrobeRule("shorts", TemperatureAbove(24.0)),
            WardrobeRule("umbrella", PrecipitationProbabilityAbove(50.0)),
        )
    }
}
