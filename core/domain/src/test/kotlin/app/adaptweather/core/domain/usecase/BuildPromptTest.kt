package app.adaptweather.core.domain.usecase

import app.adaptweather.core.domain.model.AlertSeverity
import app.adaptweather.core.domain.model.DailyForecast
import app.adaptweather.core.domain.model.HourlyForecast
import app.adaptweather.core.domain.model.TemperatureUnit
import app.adaptweather.core.domain.model.WardrobeRule
import app.adaptweather.core.domain.model.WeatherAlert
import app.adaptweather.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
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
    fun `system instruction states the five rules and the language tag`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.systemInstruction.shouldContain("\"Alert: <event>.\"")
        prompt.systemInstruction.shouldContain("\"Today will be <band>.\"")
        prompt.systemInstruction.shouldContain("Always emit this sentence")
        prompt.systemInstruction.shouldContain("at least 3°")
        prompt.systemInstruction.shouldContain("\"It will be N° warmer today.\"")
        prompt.systemInstruction.shouldContain("\"Wear <items>.\"")
        prompt.systemInstruction.shouldContain("do not compare against any previous day")
        prompt.systemInstruction.shouldContain("at least 30%")
        prompt.systemInstruction.shouldContain("\"<Type> at <HH:MM>.\"")
        prompt.systemInstruction.shouldContain("en-AU")
    }

    @Test
    fun `system instruction orders band before delta`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        val bandIndex = prompt.systemInstruction.indexOf("Today will be <band>.")
        val deltaIndex = prompt.systemInstruction.indexOf("It will be N° warmer today.")
        check(bandIndex >= 0 && deltaIndex >= 0)
        (bandIndex < deltaIndex) shouldBe true
    }

    @Test
    fun `user message uses feels-like, not raw temperatures`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("feels-like high: 17°C")
        prompt.userMessage.shouldContain("feels-like low: 10°C")
        prompt.userMessage.shouldContain("feels-like high: 23°C")
        prompt.userMessage.shouldContain("feels-like low: 15°C")
    }

    @Test
    fun `feels-like is converted to fahrenheit when requested`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.FAHRENHEIT, "en-US")
        // 17°C -> 62.6°F -> 63°F, 23°C -> 73.4°F -> 73°F
        prompt.userMessage.shouldContain("feels-like high: 63°F")
        prompt.userMessage.shouldContain("feels-like high: 73°F")
        prompt.userMessage.shouldNotContain("°C")
    }

    @Test
    fun `feels-like band emits a low-to-high range when min and max fall in different bands`() {
        // feels-like low 15 -> cool, feels-like high 23 -> mild
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("feels-like band: cool to mild")
    }

    @Test
    fun `feels-like band collapses to a single label when min and max share a band`() {
        val flat = today.copy(feelsLikeMinC = 19.0, feelsLikeMaxC = 22.0)
        val prompt = subject(flat, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("feels-like band: mild")
        prompt.userMessage.shouldNotContain("feels-like band: mild to")
    }

    @Test
    fun `feels-like band uses celsius thresholds even when display unit is fahrenheit`() {
        val cold = today.copy(feelsLikeMinC = 2.0, feelsLikeMaxC = 26.0)
        val prompt = subject(cold, yesterday, emptyList(), TemperatureUnit.FAHRENHEIT, "en-US")
        prompt.userMessage.shouldContain("feels-like band: freezing to warm")
    }

    @Test
    fun `feels-like band covers all six band labels`() {
        TemperatureBand.forCelsius(-1.0) shouldBe TemperatureBand.FREEZING
        TemperatureBand.forCelsius(3.999) shouldBe TemperatureBand.FREEZING
        TemperatureBand.forCelsius(4.0) shouldBe TemperatureBand.COLD
        TemperatureBand.forCelsius(11.999) shouldBe TemperatureBand.COLD
        TemperatureBand.forCelsius(12.0) shouldBe TemperatureBand.COOL
        TemperatureBand.forCelsius(17.999) shouldBe TemperatureBand.COOL
        TemperatureBand.forCelsius(18.0) shouldBe TemperatureBand.MILD
        TemperatureBand.forCelsius(23.999) shouldBe TemperatureBand.MILD
        TemperatureBand.forCelsius(24.0) shouldBe TemperatureBand.WARM
        TemperatureBand.forCelsius(27.999) shouldBe TemperatureBand.WARM
        TemperatureBand.forCelsius(28.0) shouldBe TemperatureBand.HOT
        TemperatureBand.forCelsius(40.0) shouldBe TemperatureBand.HOT
    }

    @Test
    fun `user message lists today's wardrobe items`() {
        val prompt = subject(
            today, yesterday,
            todayTriggeredRules = listOf(jumperRule, umbrellaRule),
            temperatureUnit = TemperatureUnit.CELSIUS,
            languageTag = "en-AU",
        )

        prompt.userMessage.shouldContain("Today's wardrobe items: jumper, umbrella")
    }

    @Test
    fun `user message marks empty wardrobe lists explicitly`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("Today's wardrobe items: (none)")
    }

    @Test
    fun `peak precipitation hour is included with type when chance is at least 30 percent`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldContain("precipitation type: rain")
        prompt.userMessage.shouldContain("precipitation peak hour: 15:00")
    }

    @Test
    fun `precipitation hints are omitted on a dry day`() {
        val dryToday = today.copy(
            precipitationProbabilityMaxPct = 5.0,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 20.0, 5.0, WeatherCondition.CLEAR)),
        )
        val prompt = subject(dryToday, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")

        prompt.userMessage.shouldNotContain("precipitation type")
        prompt.userMessage.shouldNotContain("precipitation peak hour")
    }

    @Test
    fun `condition labels are human readable`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("conditions: partly cloudy")
        prompt.userMessage.shouldContain("conditions: rain")
    }

    @Test
    fun `system instruction calls out the severe-alert rule`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.systemInstruction.shouldContain("Severe alert")
        prompt.systemInstruction.shouldContain("\"Alert: <event>.\"")
        prompt.systemInstruction.shouldContain("Extreme outranks Severe")
    }

    @Test
    fun `system instruction documents the (none) sentinel so it isn't echoed literally`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.systemInstruction.shouldContain("\"(none)\"")
        prompt.systemInstruction.shouldContain("If the list is \"(none)\", omit")
    }

    @Test
    fun `alerts block lists each alert with its severity label`() {
        val severe = WeatherAlert(
            event = "Severe Thunderstorm Warning",
            severity = AlertSeverity.SEVERE,
            headline = "Damaging hail expected",
            description = null,
            onset = Instant.parse("2026-04-25T15:00:00Z"),
            expires = Instant.parse("2026-04-25T20:00:00Z"),
        )
        val moderate = WeatherAlert(
            event = "Wind Advisory",
            severity = AlertSeverity.MODERATE,
            headline = null,
            description = null,
            onset = Instant.parse("2026-04-25T08:00:00Z"),
            expires = Instant.parse("2026-04-25T18:00:00Z"),
        )

        val prompt = subject(
            today, yesterday, emptyList(),
            TemperatureUnit.CELSIUS, "en-AU",
            alerts = listOf(severe, moderate),
        )

        prompt.userMessage.shouldContain("Severe Thunderstorm Warning [Severe]")
        prompt.userMessage.shouldContain("Wind Advisory [Moderate]")
    }

    @Test
    fun `alerts block reads (none) when no alerts are active`() {
        val prompt = subject(today, yesterday, emptyList(), TemperatureUnit.CELSIUS, "en-AU")
        prompt.userMessage.shouldContain("Severe-weather alerts: (none)")
    }
}
