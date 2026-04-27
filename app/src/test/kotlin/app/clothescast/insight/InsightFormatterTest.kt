package app.clothescast.insight

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
import app.clothescast.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Robolectric is needed here because [InsightFormatter] resolves wardrobe vocab
 * and band labels through Android string resources — the tests deliberately
 * exercise both the formatter logic *and* the `values/`, `values-en-rGB/`, and
 * `values-en-rAU/` resource files in one pass.
 *
 * `@Config(sdk = [33])` pins the API level so resource resolution is
 * reproducible across host machines.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InsightFormatterTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val subject by lazy { InsightFormatter(context) }

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
        subject.format(summary(), Region.US) shouldBe "Today will be mild."
    }

    @Test
    fun `band emits a low-to-high range when min and max fall in different bands`() {
        subject.format(
            summary(band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD)),
            Region.US,
        ) shouldBe "Today will be cool to mild."
    }

    @Test
    fun `tonight period switches the lead-in`() {
        subject.format(summary(period = ForecastPeriod.TONIGHT), Region.US) shouldBe
            "Tonight will be mild."
    }

    @Test
    fun `delta clause emits warmer with rounded degrees`() {
        val out = subject.format(
            summary(delta = DeltaClause(5, DeltaClause.Direction.WARMER)),
            Region.US,
        )
        out shouldBe "Today will be mild. It will be 5° warmer today."
    }

    @Test
    fun `delta clause emits cooler`() {
        val out = subject.format(
            summary(delta = DeltaClause(6, DeltaClause.Direction.COOLER)),
            Region.US,
        )
        out shouldBe "Today will be mild. It will be 6° cooler today."
    }

    @Test
    fun `US region renders sweater for the canonical sweater key`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear a sweater."
    }

    @Test
    fun `UK region renders jumper for the canonical sweater key`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater"))),
            Region.UK,
        ) shouldBe "Today will be mild. Wear a jumper."
    }

    @Test
    fun `AU region renders jumper for the canonical sweater key`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater"))),
            Region.AU,
        ) shouldBe "Today will be mild. Wear a jumper."
    }

    @Test
    fun `wardrobe picks 'an' before vowel-leading items`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("umbrella"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear an umbrella."
    }

    @Test
    fun `wardrobe drops the article on plural-looking items`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("shorts"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear shorts."
    }

    @Test
    fun `wardrobe joins two items with 'and' and only the first item gets an article`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater", "jacket"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear a sweater and jacket."
    }

    @Test
    fun `wardrobe Oxford-joins three items with article only on the first`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater", "jacket", "umbrella"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear a sweater, jacket, and umbrella."
    }

    @Test
    fun `wardrobe Oxford-joins four items`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater", "jacket", "shorts", "umbrella"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear a sweater, jacket, shorts, and umbrella."
    }

    @Test
    fun `wardrobe Oxford-joins four items in UK with jumper substituted for sweater`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("sweater", "jacket", "shorts", "umbrella"))),
            Region.UK,
        ) shouldBe "Today will be mild. Wear a jumper, jacket, shorts, and umbrella."
    }

    @Test
    fun `free-form custom items fall through to the heuristic article picker`() {
        // "fedora" isn't in resources; the formatter should fall through to the
        // a / an / no-article heuristic on the literal key.
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("fedora"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear a fedora."
    }

    @Test
    fun `free-form vowel-leading custom items get 'an'`() {
        subject.format(
            summary(wardrobe = WardrobeClause(listOf("anorak"))),
            Region.US,
        ) shouldBe "Today will be mild. Wear an anorak."
    }

    @Test
    fun `precip clause emits with peak hour and capitalised type`() {
        subject.format(
            summary(precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0))),
            Region.US,
        ) shouldBe "Today will be mild. Rain at 15:00."
    }

    @Test
    fun `precip clause humanises an underscored condition name`() {
        subject.format(
            summary(precip = PrecipClause(WeatherCondition.PARTLY_CLOUDY, LocalTime.NOON)),
            Region.US,
        ) shouldBe "Today will be mild. Partly cloudy at 12:00."
    }

    @Test
    fun `alert clause emits before the band`() {
        subject.format(
            summary(alert = AlertClause("Tornado Warning")),
            Region.US,
        ) shouldBe "Alert: Tornado Warning. Today will be mild."
    }

    @Test
    fun `calendar tie-in renders bring + article + time + title`() {
        subject.format(
            summary(
                wardrobe = WardrobeClause(listOf("umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.of(15, 0), "park run"),
            ),
            Region.US,
        ) shouldBe "Today will be mild. Wear an umbrella. Rain at 15:00. " +
            "Bring an umbrella for your 15:00 park run."
    }

    @Test
    fun `calendar tie-in localizes the wardrobe item too in UK`() {
        subject.format(
            summary(
                wardrobe = WardrobeClause(listOf("sweater")),
                calendarTieIn = CalendarTieInClause("sweater", LocalTime.of(15, 0), "meeting"),
            ),
            Region.UK,
        ) shouldBe "Today will be mild. Wear a jumper. Bring a jumper for your 15:00 meeting."
    }

    @Test
    fun `full insight composes alert + band + delta + wardrobe + precip in order`() {
        subject.format(
            summary(
                alert = AlertClause("Flood Warning"),
                band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD),
                delta = DeltaClause(6, DeltaClause.Direction.WARMER),
                wardrobe = WardrobeClause(listOf("sweater", "umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
            ),
            Region.US,
        ) shouldBe "Alert: Flood Warning. Today will be cool to mild. It will be 6° warmer today. " +
            "Wear a sweater and umbrella. Rain at 15:00."
    }
}
