package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.HourlyForecast
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.WardrobeRule
import com.adaptweather.core.domain.model.WeatherCondition
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class BuildPromptTest {
    private val subject = BuildPrompt()

    private val yesterday = DailyForecast(
        date = LocalDate.of(2026, 4, 24),
        temperatureMinC = 12.0,
        temperatureMaxC = 18.0,
        feelsLikeMinC = 10.0,
        feelsLikeMaxC = 17.0,
        precipitationProbabilityMaxPct = 5.0,
        precipitationMmTotal = 0.0,
        condition = WeatherCondition.PARTLY_CLOUDY,
    )

    private val today = DailyForecast(
        date = LocalDate.of(2026, 4, 25),
        temperatureMinC = 16.0,
        temperatureMaxC = 24.0,
        feelsLikeMinC = 15.0,
        feelsLikeMaxC = 23.0,
        precipitationProbabilityMaxPct = 60.0,
        precipitationMmTotal = 4.5,
        condition = WeatherCondition.RAIN,
        hourly = listOf(
            HourlyForecast(LocalTime.of(9, 0), 18.0, 16.0, 10.0, WeatherCondition.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(15, 0), 22.0, 20.0, 60.0, WeatherCondition.RAIN),
            HourlyForecast(LocalTime.of(18, 0), 19.0, 17.0, 30.0, WeatherCondition.DRIZZLE),
        ),
    )

    private val jumperRule = WardrobeRule("jumper", WardrobeRule.TemperatureBelow(18.0))
    private val umbrellaRule = WardrobeRule("umbrella", WardrobeRule.PrecipitationProbabilityAbove(50.0))

    @Test
    fun `system instruction states the three rules and the language tag`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.systemInstruction.shouldContain("at least 3°")
        prompt.systemInstruction.shouldContain("at least 30%")
        prompt.systemInstruction.shouldContain("\"It will be N° warmer today.\"")
        prompt.systemInstruction.shouldContain("\"Wear <items>.\"")
        prompt.systemInstruction.shouldContain("\"<Type> at <HH:MM>.\"")
        prompt.systemInstruction.shouldContain("en-AU")
        prompt.systemInstruction.shouldContain("empty string")
    }

    @Test
    fun `user message uses feels-like, not raw temperatures`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("feels-like high: 17°C")
        prompt.userMessage.shouldContain("feels-like low: 10°C")
        prompt.userMessage.shouldContain("feels-like high: 23°C")
        prompt.userMessage.shouldContain("feels-like low: 15°C")
    }

    @Test
    fun `feels-like is converted to fahrenheit when requested`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.FAHRENHEIT, "en-US")
        // 17°C -> 62.6°F -> 63°F, 23°C -> 73.4°F -> 73°F
        prompt.userMessage.shouldContain("feels-like high: 63°F")
        prompt.userMessage.shouldContain("feels-like high: 73°F")
        prompt.userMessage.shouldNotContain("°C")
    }

    @Test
    fun `user message lists yesterday's and today's wardrobe items`() {
        val prompt = subject(
            today, yesterday,
            yesterdayTriggeredRules = listOf(jumperRule),
            todayTriggeredRules = listOf(jumperRule, umbrellaRule),
            temperatureUnit = TemperatureUnit.CELSIUS,
            languageTag = "en-AU",
        )

        prompt.userMessage.shouldContain("Yesterday's wardrobe items: jumper")
        prompt.userMessage.shouldContain("Today's wardrobe items: jumper, umbrella")
    }

    @Test
    fun `user message marks empty wardrobe lists explicitly`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("Yesterday's wardrobe items: (none)")
        prompt.userMessage.shouldContain("Today's wardrobe items: (none)")
    }

    @Test
    fun `peak precipitation hour is included with type when chance is at least 30 percent`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("precipitation type: rain")
        prompt.userMessage.shouldContain("precipitation peak hour: 15:00")
    }

    @Test
    fun `precipitation hints are omitted on a dry day`() {
        val dryToday = today.copy(
            precipitationProbabilityMaxPct = 5.0,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 20.0, 5.0, WeatherCondition.CLEAR)),
        )
        val prompt = subject(dryToday, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldNotContain("precipitation type")
        prompt.userMessage.shouldNotContain("precipitation peak hour")
    }

    @Test
    fun `condition labels are human readable`() {
        val prompt = subject(today, yesterday, emptyList(), emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("conditions: partly cloudy")
        prompt.userMessage.shouldContain("conditions: rain")
    }
}
