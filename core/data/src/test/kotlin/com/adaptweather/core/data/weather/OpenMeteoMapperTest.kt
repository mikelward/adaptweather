package com.adaptweather.core.data.weather

import com.adaptweather.core.domain.model.WeatherCondition
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class OpenMeteoMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadFixture(): OpenMeteoResponse {
        val text = checkNotNull(javaClass.getResourceAsStream("/openmeteo_london.json")) {
            "fixture missing"
        }.bufferedReader().readText()
        return json.decodeFromString(text)
    }

    @Test
    fun `bundle preserves yesterday and today dates`() {
        val bundle = OpenMeteoMapper.toBundle(loadFixture())

        bundle.yesterday.date shouldBe LocalDate.of(2026, 4, 24)
        bundle.today.date shouldBe LocalDate.of(2026, 4, 25)
    }

    @Test
    fun `yesterday daily values are mapped`() {
        val y = OpenMeteoMapper.toBundle(loadFixture()).yesterday

        y.temperatureMinC shouldBe 12.0
        y.temperatureMaxC shouldBe 18.0
        y.precipitationProbabilityMaxPct shouldBe 5.0
        y.precipitationMmTotal shouldBe 0.0
        y.condition shouldBe WeatherCondition.PARTLY_CLOUDY
        y.hourly shouldBe emptyList()
    }

    @Test
    fun `today daily values are mapped`() {
        val t = OpenMeteoMapper.toBundle(loadFixture()).today

        t.temperatureMinC shouldBe 16.0
        t.temperatureMaxC shouldBe 24.0
        t.precipitationProbabilityMaxPct shouldBe 60.0
        t.precipitationMmTotal shouldBe 4.5
        t.condition shouldBe WeatherCondition.RAIN
    }

    @Test
    fun `today hourly is filtered to today's date only`() {
        val t = OpenMeteoMapper.toBundle(loadFixture()).today

        // Fixture has 2 hours on 2026-04-24 + 8 hours on 2026-04-25.
        t.hourly shouldHaveSize 8
        t.hourly.first().time shouldBe LocalTime.of(0, 0)
        t.hourly.last().time shouldBe LocalTime.of(21, 0)
    }

    @Test
    fun `peak rain hour is preserved for downstream BuildPrompt`() {
        val t = OpenMeteoMapper.toBundle(loadFixture()).today

        val peak = t.hourly.maxByOrNull { it.precipitationProbabilityPct }
        peak shouldNotBe null
        peak!!.time shouldBe LocalTime.of(15, 0)
        peak.precipitationProbabilityPct shouldBe 60.0
        peak.condition shouldBe WeatherCondition.RAIN
    }

    @Test
    fun `null temperatures and probabilities are tolerated as zero`() {
        val sparse = OpenMeteoResponse(
            timezone = "UTC",
            daily = DailyData(
                time = listOf("2026-04-24", "2026-04-25"),
                temperatureMin = listOf(null, 16.0),
                temperatureMax = listOf(null, 24.0),
                precipitationProbabilityMax = listOf(null, null),
                precipitationSum = listOf(null, 4.5),
                weatherCode = listOf(null, 63),
            ),
            hourly = HourlyData(
                time = listOf("2026-04-25T15:00"),
                temperature = listOf(null),
                precipitationProbability = listOf(null),
                weatherCode = listOf(null),
            ),
        )

        val bundle = OpenMeteoMapper.toBundle(sparse)

        bundle.yesterday.temperatureMinC shouldBe 0.0
        bundle.yesterday.temperatureMaxC shouldBe 0.0
        bundle.yesterday.precipitationProbabilityMaxPct shouldBe 0.0
        bundle.yesterday.condition shouldBe WeatherCondition.UNKNOWN
        bundle.today.precipitationProbabilityMaxPct shouldBe 0.0
        bundle.today.hourly.single().temperatureC shouldBe 0.0
        bundle.today.hourly.single().condition shouldBe WeatherCondition.UNKNOWN
    }

    @Test
    fun `rejects responses missing two daily entries`() {
        val short = OpenMeteoResponse(
            timezone = "UTC",
            daily = DailyData(
                time = listOf("2026-04-25"),
                temperatureMin = listOf(16.0),
                temperatureMax = listOf(24.0),
                precipitationProbabilityMax = listOf(60),
                precipitationSum = listOf(4.5),
                weatherCode = listOf(63),
            ),
            hourly = HourlyData(emptyList(), emptyList(), emptyList(), emptyList()),
        )

        try {
            OpenMeteoMapper.toBundle(short)
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }
}
