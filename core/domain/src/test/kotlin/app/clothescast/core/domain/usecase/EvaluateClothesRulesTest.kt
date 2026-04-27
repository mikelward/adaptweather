package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.WeatherCondition
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EvaluateClothesRulesTest {
    private val subject = EvaluateClothesRules()
    private val date = LocalDate.of(2026, 4, 25)

    private fun forecast(min: Double, max: Double, precip: Double = 0.0): DailyForecast =
        DailyForecast(
            date = date,
            temperatureMinC = min,
            temperatureMaxC = max,
            feelsLikeMinC = min,
            feelsLikeMaxC = max,
            precipitationProbabilityMaxPct = precip,
            precipitationMmTotal = 0.0,
            condition = WeatherCondition.CLEAR,
        )

    @Test
    fun `empty rule list yields empty result`() {
        subject(forecast(min = 5.0, max = 15.0), rules = emptyList()).shouldBeEmpty()
    }

    @Test
    fun `temperate day with no rules triggered yields empty result`() {
        subject(forecast(min = 18.0, max = 22.0), ClothesRule.DEFAULTS).shouldBeEmpty()
    }

    @Test
    fun `crisp morning warm afternoon triggers expected items in input order`() {
        val triggered = subject(forecast(min = 8.0, max = 25.0), ClothesRule.DEFAULTS)
        triggered.map { it.item }.shouldContainExactly("jumper", "jacket", "shorts")
    }

    @Test
    fun `wet cold day triggers cold-weather items and umbrella`() {
        val triggered = subject(forecast(min = 10.0, max = 16.0, precip = 70.0), ClothesRule.DEFAULTS)
        triggered.map { it.item }.shouldContainExactly("jumper", "jacket", "umbrella")
    }

    @Test
    fun `mild wet day triggers only umbrella and jumper`() {
        val triggered = subject(forecast(min = 14.0, max = 20.0, precip = 70.0), ClothesRule.DEFAULTS)
        triggered.map { it.item }.shouldContainExactly("jumper", "umbrella")
    }

    @Test
    fun `input order is preserved`() {
        val rules = listOf(
            ClothesRule("umbrella", ClothesRule.PrecipitationProbabilityAbove(50.0)),
            ClothesRule("jumper", ClothesRule.TemperatureBelow(18.0)),
            ClothesRule("jacket", ClothesRule.TemperatureBelow(12.0)),
        )
        val triggered = subject(forecast(min = 5.0, max = 12.0, precip = 80.0), rules)
        triggered.map { it.item }.shouldContainExactly("umbrella", "jumper", "jacket")
    }
}
