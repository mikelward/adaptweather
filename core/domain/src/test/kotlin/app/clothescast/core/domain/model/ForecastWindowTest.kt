package app.clothescast.core.domain.model

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class ForecastWindowTest {
    private val date = LocalDate.of(2026, 4, 25)

    private fun base(
        hourly: List<HourlyForecast> = emptyList(),
    ): DailyForecast = DailyForecast(
        date = date,
        temperatureMinC = 8.0,
        temperatureMaxC = 25.0,
        feelsLikeMinC = 6.0,
        feelsLikeMaxC = 25.0,
        precipitationProbabilityMaxPct = 60.0,
        precipitationMmTotal = 4.5,
        condition = WeatherCondition.RAIN,
        hourly = hourly,
    )

    @Test
    fun `daytime returns identity when hourly is empty`() {
        val forecast = base()
        forecast.daytime() shouldBe forecast
    }

    @Test
    fun `daytime returns identity when slice yields no entries`() {
        // Only night-time hours present; daytime slice is empty so we keep the daily
        // aggregates rather than fabricating a flat day at 0°.
        val forecast = base(
            hourly = listOf(
                HourlyForecast(LocalTime.of(2, 0), 4.0, 2.0, 0.0, WeatherCondition.CLEAR),
                HourlyForecast(LocalTime.of(22, 0), 6.0, 4.0, 0.0, WeatherCondition.CLEAR),
            ),
        )
        forecast.daytime() shouldBe forecast
    }

    @Test
    fun `daytime recomputes aggregates from the sliced hours`() {
        val forecast = base(
            hourly = listOf(
                HourlyForecast(LocalTime.of(6, 0), 4.0, 2.0, 5.0, WeatherCondition.CLEAR),
                HourlyForecast(LocalTime.of(8, 0), 12.0, 10.0, 10.0, WeatherCondition.CLEAR),
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
                HourlyForecast(LocalTime.of(20, 0), 14.0, 12.0, 30.0, WeatherCondition.CLOUDY),
            ),
        )
        val day = forecast.daytime()
        day.feelsLikeMinC shouldBe 10.0
        day.feelsLikeMaxC shouldBe 22.0
        day.temperatureMinC shouldBe 12.0
        day.temperatureMaxC shouldBe 22.0
        day.precipitationProbabilityMaxPct shouldBe 60.0
        day.hourly.map { it.time }.shouldContainExactly(LocalTime.of(8, 0), LocalTime.of(15, 0))
    }

    @Test
    fun `daytime excludes the 19_00 boundary`() {
        // 07:00 is in, 19:00 is out — the evening window owns 19:00 onwards.
        val forecast = base(
            hourly = listOf(
                HourlyForecast(LocalTime.of(7, 0), 8.0, 8.0, 5.0, WeatherCondition.CLEAR),
                HourlyForecast(LocalTime.of(19, 0), 12.0, 10.0, 5.0, WeatherCondition.CLEAR),
            ),
        )
        forecast.daytime().hourly.map { it.time }.shouldContainExactly(LocalTime.of(7, 0))
    }

    @Test
    fun `evening covers 19_00 onwards`() {
        val forecast = base(
            hourly = listOf(
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
                HourlyForecast(LocalTime.of(19, 0), 16.0, 14.0, 20.0, WeatherCondition.CLOUDY),
                HourlyForecast(LocalTime.of(22, 0), 10.0, 8.0, 0.0, WeatherCondition.CLEAR),
            ),
        )
        val night = forecast.evening()
        night.hourly.map { it.time }.shouldContainExactly(LocalTime.of(19, 0), LocalTime.of(22, 0))
        night.feelsLikeMinC shouldBe 8.0
        night.feelsLikeMaxC shouldBe 14.0
    }

    @Test
    fun `evening returns identity when no hourly entries fall in the slice`() {
        // Daytime-only hourly. Falls back to the whole-day aggregates so the caller
        // doesn't get a synthetic "evening" with the daytime numbers — and an evening
        // wardrobe rule won't fire spuriously here.
        val forecast = base(
            hourly = listOf(
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
            ),
        )
        forecast.evening() shouldBe forecast
    }

    @Test
    fun `daytime is empty-safe with no input hours`() {
        // Confidence check: a forecast that explicitly has no hourly array round-trips
        // through both windows without producing a different object.
        val forecast = base()
        forecast.evening() shouldBe forecast
        forecast.daytime().hourly.shouldBeEmpty()
    }
}
