package app.clothescast.insight

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import app.clothescast.R
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
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a structured [InsightSummary] into the spoken / displayed prose.
 *
 * Wardrobe items are stored as US-baseline keys (`sweater`, `jacket`,
 * `shorts`, `umbrella`); the formatter resolves each key against the
 * `wardrobe_item_<key>` / `wardrobe_phrase_<key>` string resources, which
 * have `values-en-rGB/` and `values-en-rAU/` overrides for the words that
 * differ across English variants. Free-form user-added items have no
 * resource match and fall through to the literal string with an
 * a / an / no-article heuristic.
 *
 * The [Region] argument to [format] picks which configuration's resources
 * are consulted: [Region.AUTO] uses the device locale (Android's existing
 * resource resolution picks `values-en-rGB/` on a UK phone, etc.); the
 * explicit [Region.US] / [Region.UK] / [Region.AU] override the device
 * locale for users whose phone language doesn't match the vocabulary they
 * want to read.
 */
class InsightFormatter(private val context: Context) {

    fun format(summary: InsightSummary, region: Region = Region.AUTO): String {
        val resources = resourcesFor(region)
        return buildList {
            summary.alert?.let { add(formatAlert(it)) }
            add(formatBand(summary.period, summary.band, resources))
            summary.delta?.let { add(formatDelta(it)) }
            summary.wardrobe?.let { add(formatWardrobe(it, resources)) }
            summary.precip?.let { add(formatPrecip(it, resources)) }
            summary.calendarTieIn?.let { add(formatCalendarTieIn(it, resources)) }
        }.joinToString(" ")
    }

    /**
     * Picks the [Resources] instance the [region] should resolve strings
     * against. [Region.AUTO] uses the platform's existing resolution (the
     * device locale); explicit US / UK / AU build a fresh
     * `Configuration`-overridden context so a user on an en-US phone who
     * picked UK still gets "jumper".
     */
    private fun resourcesFor(region: Region): Resources {
        val tag = region.bcp47 ?: return context.resources
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config).resources
    }

    private fun formatAlert(alert: AlertClause): String = "Alert: ${alert.event}."

    private fun formatBand(period: ForecastPeriod, band: BandClause, resources: Resources): String {
        val low = bandLabel(band.low, resources)
        val high = bandLabel(band.high, resources)
        val label = if (band.low == band.high) low else "$low to $high"
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

    private fun formatWardrobe(wardrobe: WardrobeClause, resources: Resources): String {
        val items = wardrobe.items
        val phrase = when (items.size) {
            1 -> phraseFor(items[0], resources)
            2 -> "${phraseFor(items[0], resources)} and ${itemFor(items[1], resources)}"
            else -> {
                val head = phraseFor(items[0], resources)
                val middle = items.subList(1, items.size - 1)
                    .joinToString(", ") { itemFor(it, resources) }
                val tail = itemFor(items.last(), resources)
                "$head, $middle, and $tail"
            }
        }
        return "Wear $phrase."
    }

    private fun formatPrecip(precip: PrecipClause, resources: Resources): String {
        val type = precip.condition.name.lowercase(Locale.ROOT).replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return "$type at ${EVENT_TIME.format(precip.time)}."
    }

    private fun formatCalendarTieIn(tieIn: CalendarTieInClause, resources: Resources): String =
        "Bring ${phraseFor(tieIn.item, resources)} for your ${EVENT_TIME.format(tieIn.time)} ${tieIn.title}."

    private fun bandLabel(band: TemperatureBand, resources: Resources): String = resources.getString(
        when (band) {
            TemperatureBand.FREEZING -> R.string.temperature_band_freezing
            TemperatureBand.COLD -> R.string.temperature_band_cold
            TemperatureBand.COOL -> R.string.temperature_band_cool
            TemperatureBand.MILD -> R.string.temperature_band_mild
            TemperatureBand.WARM -> R.string.temperature_band_warm
            TemperatureBand.HOT -> R.string.temperature_band_hot
        },
    )

    /**
     * Bare item form for joined-list positions ("..., jacket, ..."). Looks up
     * `wardrobe_item_<key>`; falls through to the literal key for free-form
     * user-added rules with no resource match.
     */
    private fun itemFor(key: String, resources: Resources): String =
        resolve(WARDROBE_ITEM_PREFIX, key, resources) ?: key

    /**
     * Article + item form for the head of the wardrobe clause and the calendar
     * tie-in ("a sweater", "an umbrella", "shorts"). For free-form items with
     * no `wardrobe_phrase_<key>` resource, falls back to the article-picker
     * heuristic on the literal key.
     */
    private fun phraseFor(key: String, resources: Resources): String =
        resolve(WARDROBE_PHRASE_PREFIX, key, resources) ?: heuristicArticle(key)

    private fun resolve(prefix: String, key: String, resources: Resources): String? {
        // Resource names are lowercase a-z + digits + underscore. Normalise the key
        // before looking up so casual mixed-case input (e.g. user typed "Sweater")
        // still hits the resource.
        val normalised = key.lowercase(Locale.ROOT).replace(NON_RESOURCE_CHARS, "_")
        if (normalised.isEmpty()) return null
        val resId = resources.getIdentifier("$prefix$normalised", "string", context.packageName)
        return if (resId != 0) resources.getString(resId) else null
    }

    /**
     * Article picker for free-form items that aren't backed by a resource:
     *  - Items ending in 's' are treated as plural (shorts, boots, gloves) and
     *    take no article.
     *  - Items starting with a vowel letter take "an"; everything else takes "a".
     */
    private fun heuristicArticle(item: String): String = when {
        item.endsWith("s", ignoreCase = true) -> item
        item.firstOrNull()?.let { it.lowercaseChar() in "aeiou" } == true -> "an $item"
        else -> "a $item"
    }

    private companion object {
        private const val WARDROBE_ITEM_PREFIX = "wardrobe_item_"
        private const val WARDROBE_PHRASE_PREFIX = "wardrobe_phrase_"
        // Anything outside [a-z0-9_] is collapsed to underscore so "sun-hat" or
        // "sun hat" both look up `wardrobe_item_sun_hat` if a resource exists.
        private val NON_RESOURCE_CHARS = Regex("[^a-z0-9_]+")

        // Locale.ROOT keeps digits ASCII regardless of device locale — under e.g.
        // Arabic locales the system default would otherwise shape "15:00" into
        // Eastern Arabic numerals, breaking the prose contract the rest of the
        // app expects.
        private val EVENT_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.ROOT)
    }
}
