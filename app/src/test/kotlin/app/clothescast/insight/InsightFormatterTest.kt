package app.clothescast.insight

import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
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
        wardrobe: WardrobeClause? = null,
        precip: PrecipClause? = null,
        calendarTieIn: CalendarTieInClause? = null,
    ) = InsightSummary(period, band, alert, delta, wardrobe, precip, calendarTieIn)

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
    fun `wardrobe with a single article-able item emits 'a <item>'`() {
        subject.format(summary(wardrobe = WardrobeClause(listOf("jumper")))) shouldBe
            "Today will be mild. Wear a jumper."
    }

    @Test
    fun `wardrobe picks 'an' before vowel-leading items`() {
        subject.format(summary(wardrobe = WardrobeClause(listOf("umbrella")))) shouldBe
            "Today will be mild. Wear an umbrella."
    }

    @Test
    fun `wardrobe drops the article on plural-looking items`() {
        subject.format(summary(wardrobe = WardrobeClause(listOf("shorts")))) shouldBe
            "Today will be mild. Wear shorts."
    }

    @Test
    fun `wardrobe joins two items with 'and' and only the first item gets an article`() {
        subject.format(summary(wardrobe = WardrobeClause(listOf("jumper", "jacket")))) shouldBe
            "Today will be mild. Wear a jumper and jacket."
    }

    @Test
    fun `wardrobe Oxford-joins three items with article only on the first`() {
        subject.format(summary(wardrobe = WardrobeClause(listOf("jumper", "jacket", "umbrella")))) shouldBe
            "Today will be mild. Wear a jumper, jacket, and umbrella."
    }

    @Test
    fun `wardrobe Oxford-joins four items`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("jumper", "jacket", "shorts", "umbrella"))),
        ) shouldBe "Today will be mild. Wear a jumper, jacket, shorts, and umbrella."
    }

    @Test
    fun `precip clause emits with peak hour and capitalised type`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)))) shouldBe
            "Today will be mild. Rain at 15:00."
    }

    @Test
    fun `precip clause humanises an underscored condition name`() {
        subject.format(summary(precip = PrecipClause(WeatherCondition.PARTLY_CLOUDY, LocalTime.NOON))) shouldBe
            "Today will be mild. Partly cloudy at 12:00."
    }

    @Test
    fun `alert clause emits before the band`() {
        val out = subject.format(summary(alert = AlertClause("Tornado Warning")))
        out shouldBe "Alert: Tornado Warning. Today will be mild."
    }

    @Test
    fun `calendar tie-in renders bring + article + time + title`() {
        val out = subject.format(
            summary(
                wardrobe = WardrobeClause(listOf("umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.of(15, 0), "park run"),
            ),
        )
        out shouldBe "Today will be mild. Wear an umbrella. Rain at 15:00. Bring an umbrella for your 15:00 park run."
    }

    @Test
    fun `full insight composes alert + band + delta + wardrobe + precip in order`() {
        val out = subject.format(
            summary(
                alert = AlertClause("Flood Warning"),
                band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD),
                delta = DeltaClause(6, DeltaClause.Direction.WARMER),
                wardrobe = WardrobeClause(listOf("jumper", "umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
            ),
        )
        out shouldBe "Alert: Flood Warning. Today will be cool to mild. It will be 6° warmer today. " +
            "Wear a jumper and umbrella. Rain at 15:00."
    }
}
