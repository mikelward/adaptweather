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
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a structured [InsightSummary] into the spoken / displayed prose.
 *
 * Living in `:app` means this is the natural place to fold in region-localized
 * vocabulary (Android string resources, BCP-47 locale picking) — the upcoming
 * Region setting will replace the hardcoded English here with `getString` calls.
 * For now the strings are inline and produce the same prose the previous
 * domain-side renderer did, byte-for-byte.
 */
class InsightFormatter {

    fun format(summary: InsightSummary): String = buildList {
        summary.alert?.let { add(formatAlert(it)) }
        add(formatBand(summary.period, summary.band))
        summary.delta?.let { add(formatDelta(it)) }
        summary.wardrobe?.let { add(formatWardrobe(it)) }
        summary.precip?.let { add(formatPrecip(it)) }
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

    private fun formatWardrobe(wardrobe: WardrobeClause): String {
        val items = wardrobe.items
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

    private fun formatPrecip(precip: PrecipClause): String {
        val type = precip.condition.name.lowercase(Locale.ROOT).replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return "$type at ${EVENT_TIME.format(precip.time)}."
    }

    private fun formatCalendarTieIn(tieIn: CalendarTieInClause): String =
        "Bring ${withArticle(tieIn.item)} for your ${EVENT_TIME.format(tieIn.time)} ${tieIn.title}."

    private fun bandLabel(band: TemperatureBand): String = when (band) {
        TemperatureBand.FREEZING -> "freezing"
        TemperatureBand.COLD -> "cold"
        TemperatureBand.COOL -> "cool"
        TemperatureBand.MILD -> "mild"
        TemperatureBand.WARM -> "warm"
        TemperatureBand.HOT -> "hot"
    }

    /**
     * Article picker for the first-listed wardrobe item.
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

    private companion object {
        // Locale.ROOT keeps digits ASCII regardless of device locale — under e.g.
        // Arabic locales the system default would otherwise shape "15:00" into
        // Eastern Arabic numerals, breaking the prose contract the rest of the
        // app expects.
        private val EVENT_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.ROOT)
    }
}
