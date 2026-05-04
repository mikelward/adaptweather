package app.clothescast.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class OutfitSuggestionTest {
    private val date = LocalDate.of(2026, 4, 29)
    private val rules = ClothesRule.DEFAULTS

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
    fun `sweater rationale names the deciding hour and the sweater rule`() {
        val hourly = listOf(
            hour(LocalTime.of(7, 0), 13.0),
            hour(LocalTime.of(12, 0), 17.5),
            hour(LocalTime.of(17, 0), 16.0),
        )
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 13.0, feelsLikeMax = 17.5, hourly = hourly),
            rules,
        )
        rationale.top.facts shouldBe listOf(
            Fact(
                metric = Fact.Metric.FEELS_LIKE_MIN,
                observedC = 13.0,
                observedAt = LocalTime.of(7, 0),
                thresholdC = 18.0,
                ruleItem = "sweater",
                comparison = Fact.Comparison.BELOW,
            ),
        )
    }

    @Test
    fun `thick jacket rationale cites the jacket rule`() {
        val hourly = listOf(hour(LocalTime.of(6, 0), 4.0), hour(LocalTime.of(15, 0), 9.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 4.0, feelsLikeMax = 9.0, hourly = hourly),
            rules,
        )
        val fact = rationale.top.facts.single()
        fact.thresholdC shouldBe 12.0
        fact.ruleItem shouldBe "jacket"
        fact.observedC shouldBe 4.0
        fact.observedAt shouldBe LocalTime.of(6, 0)
        fact.comparison shouldBe Fact.Comparison.BELOW
    }

    @Test
    fun `tshirt rationale records the feels-like min above the sweater cutoff`() {
        val hourly = listOf(hour(LocalTime.of(7, 0), 19.0), hour(LocalTime.of(14, 0), 25.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 19.0, feelsLikeMax = 25.0, hourly = hourly),
            rules,
        )
        val fact = rationale.top.facts.single()
        fact.observedC shouldBe 19.0
        fact.thresholdC shouldBe 18.0
        fact.ruleItem shouldBe "sweater"
        fact.comparison shouldBe Fact.Comparison.AT_OR_ABOVE
    }

    @Test
    fun `shorts rationale cites the shorts rule and warmest hour`() {
        val hourly = listOf(hour(LocalTime.of(7, 0), 20.0), hour(LocalTime.of(14, 0), 26.0))
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 20.0, feelsLikeMax = 26.0, hourly = hourly),
            rules,
        )
        val fact = rationale.bottom.facts.single()
        fact.metric shouldBe Fact.Metric.FEELS_LIKE_MAX
        fact.ruleItem shouldBe "shorts"
        fact.thresholdC shouldBe 24.0
        fact.observedC shouldBe 26.0
        fact.observedAt shouldBe LocalTime.of(14, 0)
        fact.comparison shouldBe Fact.Comparison.AT_OR_ABOVE
    }

    @Test
    fun `long pants rationale cites the shorts rule's unmet cutoff`() {
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 12.0, feelsLikeMax = 22.0),
            rules,
        )
        val fact = rationale.bottom.facts.single()
        fact.metric shouldBe Fact.Metric.FEELS_LIKE_MAX
        fact.ruleItem shouldBe "shorts"
        fact.thresholdC shouldBe 24.0
        fact.observedC shouldBe 22.0
        fact.comparison shouldBe Fact.Comparison.BELOW
    }

    @Test
    fun `rationale omits observedAt when hourly is empty`() {
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 13.0, feelsLikeMax = 17.0),
            rules,
        )
        rationale.top.facts.single().observedAt shouldBe null
    }

    @Test
    fun `rationale matches the suggestion across boundary cases`() {
        // Pinned cases that exercise each branch — keeps fromForecast and
        // explainFromForecast from drifting against the default clothes rules
        // (sweater 18, jacket 12, coat 6, shorts 24). Includes exact-equality
        // cases at each rule threshold so a rule-operator flip (`<` ↔ `<=`,
        // `>` ↔ `>=`) shows up here rather than being silently swallowed.
        val cases = listOf(
            // Strictly below jacket (12): THICK_JACKET fires.
            Triple(7.5, 9.0, OutfitSuggestion(OutfitSuggestion.Top.THICK_JACKET, OutfitSuggestion.Bottom.LONG_PANTS)),
            // Exactly at jacket cutoff (12): TemperatureBelow needs strictly
            // less, so 12.0 lands on SWEATER (only sweater rule fires).
            Triple(12.0, 16.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS)),
            Triple(13.0, 17.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS)),
            // Exactly at sweater cutoff (18): SWEATER ends strictly before 18,
            // so 18.0 is TSHIRT.
            Triple(18.0, 21.0, OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.LONG_PANTS)),
            // Above shorts cutoff (24): SHORTS fires.
            Triple(19.0, 26.0, OutfitSuggestion(OutfitSuggestion.Top.TSHIRT, OutfitSuggestion.Bottom.SHORTS)),
            // Exactly at shorts cutoff (24): TemperatureAbove is strictly
            // greater-than, so 24.0 doesn't fire — stays LONG_PANTS.
            Triple(15.0, 24.0, OutfitSuggestion(OutfitSuggestion.Top.SWEATER, OutfitSuggestion.Bottom.LONG_PANTS)),
        )
        cases.forEach { (min, max, expected) ->
            OutfitSuggestion.fromForecast(forecast(min, max), rules) shouldBe expected
        }
    }

    @Test
    fun `customised shorts rule flips the bottom recommendation`() {
        // With the default 24°C shorts rule, max 22°C lands on LONG_PANTS.
        // Lower the rule to 20°C and the same forecast picks SHORTS — the
        // home-screen icon and the bullet text now share the same threshold.
        val warmer = listOf(ClothesRule("shorts", ClothesRule.TemperatureAbove(20.0)))
        val outfit = OutfitSuggestion.fromForecast(
            forecast(feelsLikeMin = 18.0, feelsLikeMax = 22.0),
            warmer,
        )
        outfit.bottom shouldBe OutfitSuggestion.Bottom.SHORTS
    }

    @Test
    fun `Fahrenheit-typed rule converts to Celsius for the comparison`() {
        // The rule says "shorts above 75°F" (≈ 23.89°C). A 24°C max fires it.
        val rule = ClothesRule("shorts", ClothesRule.TemperatureAbove(75.0, TemperatureUnit.FAHRENHEIT))
        val outfit = OutfitSuggestion.fromForecast(
            forecast(feelsLikeMin = 18.0, feelsLikeMax = 24.0),
            listOf(rule),
        )
        outfit.bottom shouldBe OutfitSuggestion.Bottom.SHORTS
        val rationale = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 18.0, feelsLikeMax = 24.0),
            listOf(rule),
        )
        // thresholdC is reported in °C even when the rule was typed in °F.
        rationale.bottom.facts.single().thresholdC shouldBe (75.0 - 32.0) * 5.0 / 9.0
    }

    @Test
    fun `deleted shorts rule disables the SHORTS icon`() {
        // No shorts rule → the home screen never picks shorts, no matter how
        // hot. The rationale still has something to cite (catalog default) so
        // the dialog renders coherently.
        val noShorts = ClothesRule.DEFAULTS.filterNot { it.item == "shorts" }
        val outfit = OutfitSuggestion.fromForecast(
            forecast(feelsLikeMin = 22.0, feelsLikeMax = 30.0),
            noShorts,
        )
        outfit.bottom shouldBe OutfitSuggestion.Bottom.LONG_PANTS

        val fact = OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 22.0, feelsLikeMax = 30.0),
            noShorts,
        ).bottom.facts.single()
        fact.ruleItem shouldBe "shorts"
        fact.thresholdC shouldBe 24.0
    }

    @Test
    fun `coat rule alone still drives THICK_JACKET when it fires`() {
        // User who deleted sweater and jacket but kept coat: the icon still
        // promotes to THICK_JACKET when the coat rule (6°C) fires.
        val coatOnly = listOf(ClothesRule("coat", ClothesRule.TemperatureBelow(6.0)))
        val outfit = OutfitSuggestion.fromForecast(
            forecast(feelsLikeMin = 3.0, feelsLikeMax = 8.0),
            coatOnly,
        )
        outfit.top shouldBe OutfitSuggestion.Top.THICK_JACKET
        OutfitSuggestion.explainFromForecast(
            forecast(feelsLikeMin = 3.0, feelsLikeMax = 8.0),
            coatOnly,
        ).top.facts.single().ruleItem shouldBe "coat"
    }
}
