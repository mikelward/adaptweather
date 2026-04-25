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
        precipitationProbabilityMaxPct = 5.0,
        precipitationMmTotal = 0.0,
        condition = WeatherCondition.PARTLY_CLOUDY,
    )

    private val today = DailyForecast(
        date = LocalDate.of(2026, 4, 25),
        temperatureMinC = 16.0,
        temperatureMaxC = 24.0,
        precipitationProbabilityMaxPct = 60.0,
        precipitationMmTotal = 4.5,
        condition = WeatherCondition.RAIN,
        hourly = listOf(
            HourlyForecast(LocalTime.of(9, 0), 18.0, 10.0, WeatherCondition.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(15, 0), 22.0, 60.0, WeatherCondition.RAIN),
            HourlyForecast(LocalTime.of(18, 0), 19.0, 30.0, WeatherCondition.DRIZZLE),
        ),
    )

    @Test
    fun `system instruction carries length cap and language tag`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.systemInstruction.shouldContain("max 25 words")
        prompt.systemInstruction.shouldContain("en-AU")
    }

    @Test
    fun `user message includes both day temperatures in celsius`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("min 12°C, max 18°C")
        prompt.userMessage.shouldContain("min 16°C, max 24°C")
    }

    @Test
    fun `user message converts to fahrenheit when requested`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.FAHRENHEIT, "en-US")
        // 18C -> 64.4F -> 64°F, 24C -> 75.2F -> 75°F
        prompt.userMessage.shouldContain("max 64°F")
        prompt.userMessage.shouldContain("max 75°F")
        prompt.userMessage.shouldNotContain("°C")
    }

    @Test
    fun `peak rain hour is included when chance is at least 30 percent`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("highest rain chance around 15:00 (60%)")
    }

    @Test
    fun `peak rain hour is omitted on dry days`() {
        val dryToday = today.copy(
            precipitationProbabilityMaxPct = 5.0,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 5.0, WeatherCondition.CLEAR)),
        )
        val prompt = subject(dryToday, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldNotContain("highest rain chance")
    }

    @Test
    fun `triggered items appear in user message when present`() {
        val triggered = listOf(
            WardrobeRule("jumper", WardrobeRule.TemperatureBelow(18.0)),
            WardrobeRule("umbrella", WardrobeRule.PrecipitationProbabilityAbove(50.0)),
        )
        val prompt = subject(today, yesterday, triggered, TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("Recommended items based on user thresholds: jumper, umbrella")
    }

    @Test
    fun `no recommendation line when no rules triggered`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldNotContain("Recommended items")
    }

    @Test
    fun `condition labels are human readable`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("conditions: partly cloudy")
        prompt.userMessage.shouldContain("conditions: rain")
    }
}
