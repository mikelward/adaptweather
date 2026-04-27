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
import java.time.LocalTime
import java.util.Locale

/**
 * Renders a structured [InsightSummary] into the spoken / displayed prose.
 *
 * Living in `:app` means this is the natural place to fold in region-localized
 * vocabulary (Android string resources, BCP-47 locale picking) — the upcoming
 * Region setting will replace the hardcoded English here with `getString` calls.
 * For now the strings are inline.
 *
 * Times are rendered as natural language ("midnight", "2am", "noon", "3pm") —
 * better for TTS than "02:00" and identical text feeds both UI and speech.
 */
class InsightFormatter {

    fun format(summary: InsightSummary): String = buildList {
        summary.alert?.let { add(formatAlert(it)) }
        add(formatBand(summary.period, summary.band))
        summary.delta?.let { add(formatDelta(it)) }
        summary.clothes?.let { add(formatClothes(it)) }
        summary.precip?.let { add(formatPrecip(it, hasCalendarTieIn = summary.calendarTieIn != null)) }
        summary.calendarTieIn?.let { add(formatCalendarTieIn(it)) }
    }.joinToString(" ")

    private fun formatAlert(alert: AlertClause): String = "Alert: ${alert.event}."

    private fun formatBand(period: ForecastPeriod, band: BandClause): String {
        val label = if (band.low == band.high) bandLabel(band.low)
        else "${bandLabel(band.low)} to ${bandLabel(band.high)}"
        val lead = when (period) {
            ForecastPeriod.TODAY -> "Today"
            ForecastPeriod.TONIGHT -> "Tonight"
        }
        return "$lead will be $label."
    }

    private fun formatDelta(delta: DeltaClause): String {
        val direction = when (delta.direction) {
            DeltaClause.Direction.WARMER -> "warmer"
            DeltaClause.Direction.COOLER -> "cooler"
        }
        return "It will be ${delta.degrees}° $direction today."
    }

    private fun formatClothes(clothes: ClothesClause): String {
        val items = clothes.items
        val phrase = when (items.size) {
            1 -> withArticle(items[0])
            2 -> "${withArticle(items[0])} and ${items[1]}"
            else -> {
                val head = withArticle(items[0])
                val middle = items.subList(1, items.size - 1).joinToString(", ")
                val tail = items.last()
                "$head, $middle, and $tail"
            }
        }
        return "Wear $phrase."
    }

    private fun formatPrecip(precip: PrecipClause, hasCalendarTieIn: Boolean): String {
        val type = precip.condition.name.lowercase(Locale.ROOT).replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        // "Rain at 02:00" sounds robotic and a precise hour adds little value when
        // the user is asleep. Collapse early-morning peaks to "overnight" — but only
        // when there's no calendar tie-in pinning the time, since that clause names
        // the same hour and the two should agree.
        val timePhrase = if (precip.time.hour in OVERNIGHT_HOURS && !hasCalendarTieIn) {
            "overnight"
        } else {
            "at ${spokenTime(precip.time)}"
        }
        return "$type $timePhrase."
    }

    private fun formatCalendarTieIn(tieIn: CalendarTieInClause): String =
        "Bring ${withArticle(tieIn.item)} for your ${spokenTime(tieIn.time)} ${tieIn.title}."

    private fun bandLabel(band: TemperatureBand): String = when (band) {
        TemperatureBand.FREEZING -> "freezing"
        TemperatureBand.COLD -> "cold"
        TemperatureBand.COOL -> "cool"
        TemperatureBand.MILD -> "mild"
        TemperatureBand.WARM -> "warm"
        TemperatureBand.HOT -> "hot"
    }

    /**
     * Article picker for the first-listed clothes item.
     *  - Items ending in 's' are treated as plural (shorts, boots, gloves) and take
     *    no article.
     *  - Items starting with a vowel letter take "an"; everything else takes "a".
     * Subsequent items in the list are emitted bare per the user-preferred phrasing
     * "Wear a jumper and jacket." rather than fully grammatical "a jumper and a jacket."
     */
    private fun withArticle(item: String): String = when {
        item.endsWith("s", ignoreCase = true) -> item
        item.firstOrNull()?.let { it.lowercaseChar() in "aeiou" } == true -> "an $item"
        else -> "a $item"
    }

    /**
     * 24h LocalTime → spoken English ("midnight", "2am", "noon", "3:30pm").
     * Locale-agnostic ASCII output: Arabic locale digits would otherwise shape
     * any numeric minutes into Eastern Arabic numerals, breaking the prose
     * contract the rest of the app expects.
     */
    private fun spokenTime(time: LocalTime): String {
        val hour = time.hour
        val minute = time.minute
        if (hour == 0 && minute == 0) return "midnight"
        if (hour == 12 && minute == 0) return "noon"
        val h12 = ((hour + 11) % 12) + 1
        val suffix = if (hour < 12) "am" else "pm"
        return if (minute == 0) "$h12$suffix" else "$h12:%02d$suffix".format(Locale.ROOT, minute)
    }

    private companion object {
        // Hours treated as "overnight" when collapsing precip times. 5am is borderline
        // morning rather than night, so the band stops at 4:59.
        private val OVERNIGHT_HOURS = 0..4
    }
}
