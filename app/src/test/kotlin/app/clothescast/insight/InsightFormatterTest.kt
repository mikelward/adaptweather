package app.clothescast.insight

import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalTime

class InsightFormatterTest {
    private val subject = InsightFormatter()

    private fun summary(
        period: ForecastPeriod = ForecastPeriod.TODAY,
        band: BandClause = BandClause(TemperatureBand.MILD, TemperatureBand.MILD),
        alert: AlertClause? = null,
        delta: DeltaClause? = null,
        clothes: ClothesClause? = null,
        precip: PrecipClause? = null,
        calendarTieIn: CalendarTieInClause? = null,
    ) = InsightSummary(period, band, alert, delta, clothes, precip, calendarTieIn)

    @Test
    fun `band-only insight emits the lead-in and label`() {
        subject.format(summary()) shouldBe "Today will be mild."
    }

    @Test
    fun `band emits a low-to-high range when min and max fall in different bands`() {
        subject.format(summary(band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD))) shouldBe
            "Today will be cool to mild."
    }

    @Test
    fun `tonight period switches the lead-in`() {
        subject.format(summary(period = ForecastPeriod.TONIGHT)) shouldBe "Tonight will be mild."
    }

    @Test
    fun `delta clause emits warmer with rounded degrees`() {
        val out = subject.format(summary(delta = DeltaClause(5, DeltaClause.Direction.WARMER)))
        out shouldBe "Today will be mild. It will be 5° warmer today."
    }

    @Test
    fun `delta clause emits cooler`() {
        val out = subject.format(summary(delta = DeltaClause(6, DeltaClause.Direction.COOLER)))
        out shouldBe "Today will be mild. It will be 6° cooler today."
    }

    @Test
    fun `clothes with a single article-able item emits 'a jumper'`() {
        subject.format(summary(clothes = ClothesClause(listOf("jumper")))) shouldBe
            "Today will be mild. Wear a jumper."
    }

    @Test
    fun `clothes picks 'an' before vowel-leading items`() {
        subject.format(summary(clothes = ClothesClause(listOf("umbrella")))) shouldBe
            "Today will be mild. Wear an umbrella."
    }

    @Test
    fun `clothes drops the article on plural-looking items`() {
        subject.format(summary(clothes = ClothesClause(listOf("shorts")))) shouldBe
            "Today will be mild. Wear shorts."
    }

    @Test
    fun `clothes joins two items with 'and' and only the first item gets an article`() {
        subject.format(summary(clothes = ClothesClause(listOf("jumper", "jacket")))) shouldBe
            "Today will be mild. Wear a jumper and jacket."
    }

    @Test
    fun `clothes Oxford-joins three items with article only on the first`() {
        subject.format(summary(clothes = ClothesClause(listOf("jumper", "jacket", "umbrella")))) shouldBe
            "Today will be mild. Wear a jumper, jacket, and umbrella."
    }

    @Test
    fun `clothes Oxford-joins four items`() {
        subject.format(
            summary(clothes = ClothesClause(listOf("jumper", "jacket", "shorts", "umbrella"))),
        ) shouldBe "Today will be mild. Wear a jumper, jacket, shorts, and umbrella."
    }

    @Test
    fun `precip clause emits with spoken peak hour and capitalised type`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)))) shouldBe
            "Today will be mild. Rain at 3pm."
    }

    @Test
    fun `precip clause says 'noon' for 12-00`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.DRIZZLE, LocalTime.NOON))) shouldBe
            "Today will be mild. Drizzle at noon."
    }

    @Test
    fun `precip clause says 'overnight' for early-morning peak when no calendar tie-in`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(2, 0)))) shouldBe
            "Today will be mild. Rain overnight."
    }

    @Test
    fun `precip clause says 'overnight' for midnight peak when no calendar tie-in`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.SNOW, LocalTime.MIDNIGHT))) shouldBe
            "Today will be mild. Snow overnight."
    }

    @Test
    fun `precip clause names the hour even overnight when a calendar tie-in pins the same time`() {
        // The tie-in clause spells out "your 2am yoga class" — the precip clause
        // should agree on the time rather than collapse to "overnight".
        val out = subject.format(
            summary(
                clothes = ClothesClause(listOf("umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(2, 0)),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.of(2, 0), "yoga class"),
            ),
        )
        out shouldBe "Today will be mild. Wear an umbrella. Rain at 2am. Bring an umbrella for your 2am yoga class."
    }

    @Test
    fun `precip clause uses 'midnight' when calendar tie-in pins 00-00`() {
        val out = subject.format(
            summary(
                clothes = ClothesClause(listOf("umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.MIDNIGHT),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.MIDNIGHT, "stargazing"),
            ),
        )
        out shouldBe "Today will be mild. Wear an umbrella. Rain at midnight. Bring an umbrella for your midnight stargazing."
    }

    @Test
    fun `precip clause uses 12-hour pm form for late-evening peak`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(23, 0)))) shouldBe
            "Today will be mild. Rain at 11pm."
    }

    @Test
    fun `precip clause renders non-zero minutes`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 30)))) shouldBe
            "Today will be mild. Rain at 3:30pm."
    }

    @Test
    fun `alert clause emits before the band`() {
        val out = subject.format(summary(alert = AlertClause("Tornado Warning")))
        out shouldBe "Alert: Tornado Warning. Today will be mild."
    }

    @Test
    fun `calendar tie-in renders bring + article + spoken time + title`() {
        val out = subject.format(
            summary(
                clothes = ClothesClause(listOf("umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.of(15, 0), "park run"),
            ),
        )
        out shouldBe "Today will be mild. Wear an umbrella. Rain at 3pm. Bring an umbrella for your 3pm park run."
    }

    @Test
    fun `full insight composes alert + band + delta + clothes + precip in order`() {
        val out = subject.format(
            summary(
                alert = AlertClause("Flood Warning"),
                band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD),
                delta = DeltaClause(6, DeltaClause.Direction.WARMER),
                clothes = ClothesClause(listOf("jumper", "umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
            ),
        )
        out shouldBe "Alert: Flood Warning. Today will be cool to mild. It will be 6° warmer today. " +
            "Wear a jumper and umbrella. Rain at 3pm."
    }
}
