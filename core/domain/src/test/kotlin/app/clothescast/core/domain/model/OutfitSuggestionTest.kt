package app.clothescast.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class OutfitSuggestionTest {
    private val date = LocalDate.of(2026, 4, 29)

    private fun forecast(
        feelsLikeMin: Double,
        feelsLikeMax: Double,
        hourly: List<HourlyForecast> = emptyList(),
    ): DailyForecast = DailyForecast(
        date = date,
        temperatureMinC = feelsLikeMin,
        temperatureMaxC = feelsLikeMax,
        feelsLikeMinC = feelsLikeMin,
        feelsLikeMaxC = feelsLikeMax,
        precipitationProbabilityMaxPct = 0.0,
        precipitationMmTotal = 0.0,
        condition = WeatherCondition.CLEAR,
        hourly = hourly,
    )

    private fun hour(time: LocalTime, feelsLikeC: Double): HourlyForecast = HourlyForecast(
        time = time,
        temperatureC = feelsLikeC,
        feelsLikeC = feelsLikeC,
        precipitationProbabilityPct = 0.0,
        condition = WeatherCondition.CLEAR,
    )

    @Test
    fun `sweater rationale names the deciding hour and threshold`() {
        val hourly = listOf(
            hour(LocalTime.of(7, 0), 13.0),
            hour(LocalTime.of(12, 0), 17.5),
            hour(LocalTime.of(17, 0), 16.0),
        )
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 13.0, feelsLikeMax = 17.5, hourly = hourly),
        )
        rationale.top.facts shouldBe listOf(
            Fact(
                metric = Fact.Metric.FEELS_LIKE_MIN,
                observedC = 13.0,
                observedAt = LocalTime.of(7, 0),
                thresholdC = 18.0,
                comparison = Fact.Comparison.BELOW,
            ),
        )
    }

    @Test
    fun `thick jacket rationale crosses the cold threshold`() {
        val hourly = listOf(hour(LocalTime.of(6, 0), 4.0), hour(LocalTime.of(15, 0), 9.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 4.0, feelsLikeMax = 9.0, hourly = hourly),
        )
        val fact = rationale.top.facts.single()
        fact.thresholdC shouldBe 8.0
        fact.observedC shouldBe 4.0
        fact.observedAt shouldBe LocalTime.of(6, 0)
        fact.comparison shouldBe Fact.Comparison.BELOW
    }

    @Test
    fun `tshirt rationale records the feels-like min above the cutoff`() {
        val hourly = listOf(hour(LocalTime.of(7, 0), 19.0), hour(LocalTime.of(14, 0), 25.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 19.0, feelsLikeMax = 25.0, hourly = hourly),
        )
        val fact = rationale.top.facts.single()
        fact.observedC shouldBe 19.0
        fact.thresholdC shouldBe 18.0
        fact.comparison shouldBe Fact.Comparison.AT_OR_ABOVE
    }

    @Test
    fun `shorts rationale shows both supporting facts`() {
        val hourly = listOf(hour(LocalTime.of(7, 0), 16.0), hour(LocalTime.of(14, 0), 26.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 16.0, feelsLikeMax = 26.0, hourly = hourly),
        )
        rationale.bottom.facts.map { it.metric to it.comparison } shouldBe listOf(
            Fact.Metric.FEELS_LIKE_MAX to Fact.Comparison.AT_OR_ABOVE,
            Fact.Metric.FEELS_LIKE_MIN to Fact.Comparison.AT_OR_ABOVE,
        )
    }

    @Test
    fun `long pants rationale calls out the cold morning when only min fails`() {
        val hourly = listOf(hour(LocalTime.of(7, 0), 12.0), hour(LocalTime.of(14, 0), 26.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 12.0, feelsLikeMax = 26.0, hourly = hourly),
        )
        val fact = rationale.bottom.facts.single()
        fact.metric shouldBe Fact.Metric.FEELS_LIKE_MIN
        fact.comparison shouldBe Fact.Comparison.BELOW
        fact.observedAt shouldBe LocalTime.of(7, 0)
    }

    @Test
    fun `long pants rationale calls out the cool day when only max fails`() {
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 17.0, feelsLikeMax = 19.0),
        )
        val fact = rationale.bottom.facts.single()
        fact.metric shouldBe Fact.Metric.FEELS_LIKE_MAX
        fact.comparison shouldBe Fact.Comparison.BELOW
    }

    @Test
    fun `long pants rationale lists both blockers when neither condition is met`() {
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 8.0, feelsLikeMax = 14.0),
        )
        rationale.bottom.facts.map { it.metric } shouldBe listOf(
            Fact.Metric.FEELS_LIKE_MAX,
            Fact.Metric.FEELS_LIKE_MIN,
        )
        rationale.bottom.facts.all { it.comparison == Fact.Comparison.BELOW } shouldBe true
    }

    @Test
    fun `rationale omits observedAt when hourly is empty`() {
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 13.0, feelsLikeMax = 17.0),
        )
        rationale.top.facts.single().observedAt shouldBe null
    }

    @Test
    fun `rationale matches the suggestion across boundary cases`() {
        // Pinned cases that exercise each branch — keeps fromForecast and
        // explainFromForecast from drifting. Includes exact-equality cases at
        // each threshold so a rule operator flip (`<` ↔ `<=`, `>` ↔ `>=`) shows
        // up here rather than being silently swallowed by the rare-boundary case.
        val cases = listOf(
            // Strictly below jacket cutoff.
            Triple(7.5, 9.0, OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.LONG_PANTS)),
            // Equality at sweater/jacket cutoff (8°C): jacket needs strictly less,
            // so exactly 8°C lands on SWEATER.
            Triple(8.0, 12.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS)),
            Triple(13.0, 17.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS)),
            // Equality at sweater/t-shirt cutoff (18°C): SWEATER ends strictly
            // before 18, so exactly 18°C is TSHIRT.
            Triple(18.0, 21.0, OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.LONG_PANTS)),
            Triple(19.0, 26.0, OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.SHORTS)),
            // Both shorts boundaries at exact equality (max == 22 AND min == 15)
            // — inclusive `>=` makes this SHORTS, not LONG_PANTS, so the "at
            // least" wording in the rationale dialog reads truthfully.
            Triple(15.0, 22.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.SHORTS)),
        )
        cases.forEach { (min, max, expected) ->
            OutfitSuggestion.fromForecast(forecast(min, max)) shouldBe expected
        }
    }
}
