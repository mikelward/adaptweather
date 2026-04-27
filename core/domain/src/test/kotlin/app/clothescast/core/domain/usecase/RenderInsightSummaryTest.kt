package app.clothescast.core.domain.usecase

import app.clothescast.core.domain.model.AlertSeverity
import app.clothescast.core.domain.model.CalendarEvent
import app.clothescast.core.domain.model.DailyForecast
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.WardrobeRule
import app.clothescast.core.domain.model.WeatherAlert
import app.clothescast.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RenderInsightSummaryTest {
    private val subject = RenderInsightSummary()

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

    private val mildToday = DailyForecast(
        date = LocalDate.of(2026, 4, 25),
        temperatureMinC = 16.0,
        temperatureMaxC = 22.0,
        feelsLikeMinC = 18.0,
        feelsLikeMaxC = 22.0,
        precipitationProbabilityMaxPct = 5.0,
        precipitationMmTotal = 0.0,
        condition = WeatherCondition.PARTLY_CLOUDY,
    )

    private val jumperRule = WardrobeRule("jumper", WardrobeRule.TemperatureBelow(18.0))
    private val jacketRule = WardrobeRule("jacket", WardrobeRule.TemperatureBelow(12.0))
    private val umbrellaRule = WardrobeRule("umbrella", WardrobeRule.PrecipitationProbabilityAbove(50.0))
    private val shortsRule = WardrobeRule("shorts", WardrobeRule.TemperatureAbove(24.0))

    @Test
    fun `band sentence is always emitted`() {
        // Other rules might also fire on this fixture; this test only cares that the
        // band sentence is present.
        subject(mildToday, yesterday, emptyList()).shouldContain("Today will be mild.")
    }

    @Test
    fun `band sentence emits a low-to-high range when min and max fall in different bands`() {
        val today = mildToday.copy(feelsLikeMinC = 15.0, feelsLikeMaxC = 23.0)
        subject(today, yesterday, emptyList()).shouldContain("Today will be cool to mild.")
    }

    @Test
    fun `delta sentence emits when feels-like high differs by at least 3 degrees`() {
        // yesterday max 17, today max 22 → +5 warmer.
        // Match the lows so the high delta is the larger absolute one and wins.
        val today = mildToday.copy(feelsLikeMaxC = 22.0, feelsLikeMinC = yesterday.feelsLikeMinC)
        val out = subject(today, yesterday, emptyList())
        out.shouldContain("It will be 5° warmer today.")
    }

    @Test
    fun `delta sentence is omitted when the unrounded delta is under 3 even if it rounds to 3`() {
        // 2.6° rounds to 3 — but the rule is "≥3° true delta", not "≥3° after rounding".
        // Match the lows so only the high delta matters.
        val today = mildToday.copy(
            feelsLikeMaxC = yesterday.feelsLikeMaxC + 2.6,
            feelsLikeMinC = yesterday.feelsLikeMinC,
        )
        subject(today, yesterday, emptyList()).shouldNotContain("warmer")
    }

    @Test
    fun `delta sentence is omitted when both deltas are under 3 degrees`() {
        // yesterday max 17, today max 19 → +2 (rounds to 2, omit)
        // yesterday min 10, today min 12 → +2
        val today = mildToday.copy(feelsLikeMinC = 12.0, feelsLikeMaxC = 19.0)
        subject(today, yesterday, emptyList()).shouldNotContain("warmer")
    }

    @Test
    fun `delta uses the larger absolute delta when high and low disagree`() {
        // yesterday: max 17, min 10
        // today:    max 16 (-1), min 4 (-6) → cooler 6
        val today = mildToday.copy(feelsLikeMinC = 4.0, feelsLikeMaxC = 16.0)
        val out = subject(today, yesterday, emptyList())
        out.shouldContain("It will be 6° cooler today.")
    }

    @Test
    fun `band sentence comes before delta sentence`() {
        val today = mildToday.copy(feelsLikeMaxC = 22.0)
        val out = subject(today, yesterday, emptyList())
        val bandIdx = out.indexOf("Today will be")
        val deltaIdx = out.indexOf("It will be")
        check(bandIdx >= 0 && deltaIdx >= 0)
        (bandIdx < deltaIdx) shouldBe true
    }

    @Test
    fun `wardrobe sentence drops with a single article-able item`() {
        val out = subject(mildToday, yesterday, listOf(jumperRule))
        out.shouldContain("Wear a jumper.")
    }

    @Test
    fun `wardrobe sentence picks 'an' before vowel-leading items`() {
        val out = subject(mildToday, yesterday, listOf(umbrellaRule))
        out.shouldContain("Wear an umbrella.")
    }

    @Test
    fun `wardrobe sentence drops the article on plural-looking items`() {
        val out = subject(mildToday, yesterday, listOf(shortsRule))
        out.shouldContain("Wear shorts.")
        out.shouldNotContain("a shorts")
    }

    @Test
    fun `wardrobe sentence joins two items with 'and' and only the first item gets an article`() {
        val out = subject(mildToday, yesterday, listOf(jumperRule, jacketRule))
        out.shouldContain("Wear a jumper and jacket.")
    }

    @Test
    fun `wardrobe sentence Oxford-joins three or more items with article only on the first`() {
        val out = subject(mildToday, yesterday, listOf(jumperRule, jacketRule, umbrellaRule))
        out.shouldContain("Wear a jumper, jacket, and umbrella.")
    }

    @Test
    fun `wardrobe sentence is omitted when no rules trigger`() {
        subject(mildToday, yesterday, emptyList()).shouldNotContain("Wear")
    }

    @Test
    fun `precipitation sentence emits with peak hour and capitalised type when chance is at least 30 percent`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(
                HourlyForecast(LocalTime.of(9, 0), 18.0, 18.0, 10.0, WeatherCondition.PARTLY_CLOUDY),
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
            ),
        )
        subject(today, yesterday, emptyList()).shouldContain("Rain at 15:00.")
    }

    @Test
    fun `precipitation sentence falls back to noon when no hourly entry crosses the threshold`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 40.0,
            condition = WeatherCondition.DRIZZLE,
            hourly = emptyList(),
        )
        subject(today, yesterday, emptyList()).shouldContain("Drizzle at 12:00.")
    }

    @Test
    fun `precipitation sentence is omitted on a dry day`() {
        subject(mildToday, yesterday, emptyList()).shouldNotContain(" at ")
    }

    @Test
    fun `severe alert sentence is emitted before the band sentence and uses the highest-severity event`() {
        val severe = WeatherAlert(
            event = "Severe Thunderstorm Warning",
            severity = AlertSeverity.SEVERE,
            headline = null, description = null,
            onset = Instant.parse("2026-04-25T15:00:00Z"),
            expires = Instant.parse("2026-04-25T20:00:00Z"),
        )
        val extreme = WeatherAlert(
            event = "Tornado Warning",
            severity = AlertSeverity.EXTREME,
            headline = null, description = null,
            onset = Instant.parse("2026-04-25T15:00:00Z"),
            expires = Instant.parse("2026-04-25T20:00:00Z"),
        )
        val out = subject(mildToday, yesterday, emptyList(), alerts = listOf(severe, extreme))
        out.startsWith("Alert: Tornado Warning.") shouldBe true
    }

    @Test
    fun `non-severe alerts are ignored`() {
        val moderate = WeatherAlert(
            event = "Wind Advisory",
            severity = AlertSeverity.MODERATE,
            headline = null, description = null,
            onset = Instant.parse("2026-04-25T08:00:00Z"),
            expires = Instant.parse("2026-04-25T18:00:00Z"),
        )
        subject(mildToday, yesterday, emptyList(), alerts = listOf(moderate))
            .shouldNotContain("Alert:")
    }

    @Test
    fun `full insight composes alert + band + delta + wardrobe + precipitation in order`() {
        val today = mildToday.copy(
            feelsLikeMinC = 15.0,
            feelsLikeMaxC = 23.0,
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
            ),
        )
        val severe = WeatherAlert(
            event = "Flood Warning",
            severity = AlertSeverity.SEVERE,
            headline = null, description = null,
            onset = Instant.parse("2026-04-25T15:00:00Z"),
            expires = Instant.parse("2026-04-25T20:00:00Z"),
        )
        val out = subject(
            today, yesterday,
            todayTriggeredRules = listOf(jumperRule, umbrellaRule),
            alerts = listOf(severe),
        )
        out shouldBe "Alert: Flood Warning. Today will be cool to mild. It will be 6° warmer today. " +
            "Wear a jumper and umbrella. Rain at 15:00."
    }

    @Test
    fun `calendar tie-in fires when wardrobe + precip + overlapping event all present`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(
                HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN),
            ),
        )
        val event = CalendarEvent(
            title = "park run",
            start = LocalTime.of(14, 30),
            end = LocalTime.of(16, 0),
        )
        val out = subject(today, yesterday, listOf(umbrellaRule), events = listOf(event))
        out.shouldContain("Bring an umbrella for your 15:00 park run.")
    }

    @Test
    fun `calendar tie-in prefers umbrella over the first listed item when both apply`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN)),
        )
        val event = CalendarEvent("standup", LocalTime.of(14, 0), LocalTime.of(16, 0))
        val out = subject(today, yesterday, listOf(jumperRule, umbrellaRule), events = listOf(event))
        out.shouldContain("Bring an umbrella for your 15:00 standup.")
    }

    @Test
    fun `calendar tie-in falls back to the first item when umbrella is not on the list`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN)),
        )
        val event = CalendarEvent("park run", LocalTime.of(14, 0), LocalTime.of(16, 0))
        val out = subject(today, yesterday, listOf(jumperRule, jacketRule), events = listOf(event))
        out.shouldContain("Bring a jumper for your 15:00 park run.")
    }

    @Test
    fun `calendar tie-in is omitted when no event overlaps the precip peak`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN)),
        )
        val event = CalendarEvent("breakfast", LocalTime.of(8, 0), LocalTime.of(9, 0))
        subject(today, yesterday, listOf(umbrellaRule), events = listOf(event))
            .shouldNotContain("Bring")
    }

    @Test
    fun `calendar tie-in is omitted when no wardrobe rule fires`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN)),
        )
        val event = CalendarEvent("park run", LocalTime.of(14, 30), LocalTime.of(16, 0))
        subject(today, yesterday, emptyList(), events = listOf(event))
            .shouldNotContain("Bring")
    }

    @Test
    fun `calendar tie-in is omitted on a dry day even when an event exists`() {
        val event = CalendarEvent("park run", LocalTime.of(11, 0), LocalTime.of(13, 0))
        subject(mildToday, yesterday, listOf(umbrellaRule), events = listOf(event))
            .shouldNotContain("Bring")
    }

    @Test
    fun `calendar tie-in skips all-day events`() {
        val today = mildToday.copy(
            precipitationProbabilityMaxPct = 60.0,
            condition = WeatherCondition.RAIN,
            hourly = listOf(HourlyForecast(LocalTime.of(15, 0), 22.0, 22.0, 60.0, WeatherCondition.RAIN)),
        )
        val holiday = CalendarEvent("public holiday", LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true)
        subject(today, yesterday, listOf(umbrellaRule), events = listOf(holiday))
            .shouldNotContain("Bring")
    }

    @Test
    fun `evening tie-in fires when evening rules trigger and a qualifying event is supplied`() {
        // Caller-side filtering (allDay/location/end-after-19:00) lives in the use-case;
        // the renderer just trusts what it's handed.
        val event = CalendarEvent(
            title = "dinner",
            start = LocalTime.of(20, 0),
            end = LocalTime.of(22, 0),
            location = "Trattoria",
        )
        val out = subject(
            mildToday, yesterday,
            todayTriggeredRules = emptyList(),
            eveningTriggeredRules = listOf(jacketRule),
            eveningEvents = listOf(event),
        )
        out.shouldContain("Bring a jacket for your 20:00 dinner.")
    }

    @Test
    fun `evening tie-in joins multiple items like the wardrobe sentence`() {
        val event = CalendarEvent(
            title = "concert",
            start = LocalTime.of(21, 0),
            end = LocalTime.of(23, 0),
            location = "Royal Albert Hall",
        )
        val out = subject(
            mildToday, yesterday,
            todayTriggeredRules = emptyList(),
            eveningTriggeredRules = listOf(jacketRule, umbrellaRule),
            eveningEvents = listOf(event),
        )
        out.shouldContain("Bring a jacket and umbrella for your 21:00 concert.")
    }

    @Test
    fun `evening tie-in picks the earliest event when multiple qualify`() {
        val late = CalendarEvent("late drinks", LocalTime.of(22, 0), LocalTime.of(23, 30), location = "Pub")
        val early = CalendarEvent("dinner", LocalTime.of(20, 0), LocalTime.of(21, 30), location = "Trattoria")
        val out = subject(
            mildToday, yesterday,
            todayTriggeredRules = emptyList(),
            eveningTriggeredRules = listOf(jacketRule),
            eveningEvents = listOf(late, early),
        )
        out.shouldContain("for your 20:00 dinner.")
        out.shouldNotContain("for your 22:00 late drinks.")
    }

    @Test
    fun `evening tie-in is omitted when no evening rules trigger`() {
        // Mild evening + going-out plans → no jacket nudge. The whole point of the
        // rule is "you're going out *and* you'd want something extra"; it must stay
        // quiet on a balmy night out.
        val event = CalendarEvent("dinner", LocalTime.of(20, 0), LocalTime.of(22, 0), location = "Trattoria")
        subject(
            mildToday, yesterday,
            todayTriggeredRules = emptyList(),
            eveningTriggeredRules = emptyList(),
            eveningEvents = listOf(event),
        ).shouldNotContain("Bring")
    }

    @Test
    fun `evening tie-in is omitted when no qualifying event is supplied`() {
        // Cold evening but the user is staying in — no nag.
        subject(
            mildToday, yesterday,
            todayTriggeredRules = emptyList(),
            eveningTriggeredRules = listOf(jacketRule),
            eveningEvents = emptyList(),
        ).shouldNotContain("Bring")
    }

    @Test
    fun `temperature band classifier covers all six bands at boundaries`() {
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
}
