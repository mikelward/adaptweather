package com.adaptweather.core.domain.usecase

import com.adaptweather.core.domain.model.DailyForecast
import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.Schedule
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.UserPreferences
import com.adaptweather.core.domain.model.WardrobeRule
import com.adaptweather.core.domain.model.WeatherCondition
import com.adaptweather.core.domain.repository.ForecastBundle
import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.repository.WeatherRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GenerateDailyInsightTest {
    private val clockInstant = Instant.parse("2026-04-25T07:00:00Z")
    private val clock = Clock.fixed(clockInstant, ZoneOffset.UTC)

    private val london = Location(latitude = 51.5074, longitude = -0.1278, displayName = "London")

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
        temperatureMinC = 8.0,
        temperatureMaxC = 25.0,
        feelsLikeMinC = 6.0,
        feelsLikeMaxC = 24.0,
        precipitationProbabilityMaxPct = 60.0,
        precipitationMmTotal = 4.5,
        condition = WeatherCondition.RAIN,
    )

    private val prefs = UserPreferences(
        schedule = Schedule.default(ZoneOffset.UTC),
        deliveryMode = DeliveryMode.NOTIFICATION_ONLY,
        temperatureUnit = TemperatureUnit.CELSIUS,
        distanceUnit = DistanceUnit.KILOMETERS,
        wardrobeRules = WardrobeRule.DEFAULTS,
    )

    private class FakeWeatherRepository(private val bundle: ForecastBundle) : WeatherRepository {
        var lastQueriedLocation: Location? = null
            private set

        override suspend fun fetchForecast(location: Location): ForecastBundle {
            lastQueriedLocation = location
            return bundle
        }
    }

    private class FakeInsightGenerator(private val response: String) : InsightGenerator {
        var lastPrompt: Prompt? = null
            private set

        override suspend fun generate(prompt: Prompt): String {
            lastPrompt = prompt
            return response
        }
    }

    @Test
    fun `produces insight with rule items, summary, clock-based timestamp, today's date`() = runTest {
        val weather = FakeWeatherRepository(ForecastBundle(today, yesterday))
        val gen = FakeInsightGenerator("  Cooler this morning but warmer than yesterday's peak — bring a jumper.  ")
        val subject = GenerateDailyInsight(weather, gen, clock = clock)

        val insight = subject(london, prefs, languageTag = "en-AU")

        insight.summary shouldBe "Cooler this morning but warmer than yesterday's peak — bring a jumper."
        insight.recommendedItems.shouldContainExactly("jumper", "jacket", "shorts", "umbrella")
        insight.generatedAt shouldBe clockInstant
        insight.forDate shouldBe today.date
    }

    @Test
    fun `forwards location to weather repository`() = runTest {
        val weather = FakeWeatherRepository(ForecastBundle(today, yesterday))
        val gen = FakeInsightGenerator("ok")
        val subject = GenerateDailyInsight(weather, gen, clock = clock)

        subject(london, prefs, languageTag = "en-AU")

        weather.lastQueriedLocation shouldBe london
    }

    @Test
    fun `passes language tag and units through to the prompt`() = runTest {
        val weather = FakeWeatherRepository(ForecastBundle(today, yesterday))
        val gen = FakeInsightGenerator("ok")
        val subject = GenerateDailyInsight(weather, gen, clock = clock)

        subject(london, prefs.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT), languageTag = "en-US")

        val prompt = checkNotNull(gen.lastPrompt)
        prompt.systemInstruction.shouldContain("en-US")
        prompt.userMessage.shouldContain("°F")
    }

    @Test
    fun `recommended items reflect rule evaluation, not raw rule list`() = runTest {
        val mildToday = today.copy(
            temperatureMinC = 19.0,
            temperatureMaxC = 22.0,
            feelsLikeMinC = 19.0,
            feelsLikeMaxC = 22.0,
            precipitationProbabilityMaxPct = 10.0,
        )
        val weather = FakeWeatherRepository(ForecastBundle(mildToday, yesterday))
        val gen = FakeInsightGenerator("ok")
        val subject = GenerateDailyInsight(weather, gen, clock = clock)

        val insight = subject(london, prefs, languageTag = "en-AU")

        insight.recommendedItems shouldBe emptyList()
    }
}
