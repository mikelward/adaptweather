package com.adaptweather.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WardrobeRuleTest {
    private val date = LocalDate.of(2026, 4, 25)

    private fun forecast(min: Double, max: Double, precip: Double = 0.0): DailyForecast =
        DailyForecast(
            date = date,
            temperatureMinC = min,
            temperatureMaxC = max,
            precipitationProbabilityMaxPct = precip,
            precipitationMmTotal = 0.0,
            condition = WeatherCondition.CLEAR,
        )

    @Test
    fun `temperature below applies when daily min is colder`() {
        val rule = WardrobeRule("jumper", WardrobeRule.TemperatureBelow(18.0))
        rule.appliesTo(forecast(min = 14.0, max = 22.0)) shouldBe true
    }

    @Test
    fun `temperature below does not apply when daily min meets threshold`() {
        val rule = WardrobeRule("jumper", WardrobeRule.TemperatureBelow(18.0))
        rule.appliesTo(forecast(min = 18.0, max = 22.0)) shouldBe false
    }

    @Test
    fun `temperature above applies when daily max is warmer`() {
        val rule = WardrobeRule("shorts", WardrobeRule.TemperatureAbove(24.0))
        rule.appliesTo(forecast(min = 12.0, max = 26.5)) shouldBe true
    }

    @Test
    fun `temperature above does not apply when daily max meets threshold`() {
        val rule = WardrobeRule("shorts", WardrobeRule.TemperatureAbove(24.0))
        rule.appliesTo(forecast(min = 12.0, max = 24.0)) shouldBe false
    }

    @Test
    fun `precipitation rule uses peak probability`() {
        val rule = WardrobeRule("umbrella", WardrobeRule.PrecipitationProbabilityAbove(50.0))
        rule.appliesTo(forecast(min = 10.0, max = 18.0, precip = 65.0)) shouldBe true
        rule.appliesTo(forecast(min = 10.0, max = 18.0, precip = 30.0)) shouldBe false
    }

    @Test
    fun `defaults cover the four MVP cases`() {
        val items = WardrobeRule.DEFAULTS.map { it.item }
        items shouldBe listOf("jumper", "jacket", "shorts", "umbrella")
    }

    @Test
    fun `cold morning warm afternoon triggers both jumper and shorts`() {
        // Realistic spring day: chilly start, warm peak.
        val day = forecast(min = 8.0, max = 25.0)
        val triggered = WardrobeRule.DEFAULTS.filter { it.appliesTo(day) }.map { it.item }
        triggered shouldBe listOf("jumper", "jacket", "shorts")
    }
}
