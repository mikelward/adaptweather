package app.clothescast.insight

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import app.clothescast.R
import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.EveningEventTieInClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WeatherCondition
import java.time.LocalTime
import java.util.Locale

/**
 * Renders a structured [InsightSummary] into the spoken / displayed prose.
 *
 * Strings live in `values[-xx]/strings.xml`; the formatter only composes them.
 * The injected [resources] is expected to have a Configuration whose locale
 * matches [locale] — call sites use [forRegion] (or [forContext]) to obtain a
 * properly localized Resources for the user's [Region] choice.
 *
 * Article picking (English "a sweater" / "an umbrella" / bare "shorts") is
 * locale-specific and dispatched via [ClothesPhraser.forLocale]. Languages
 * without an explicit phraser fall back to a no-article join.
 *
 * Times are rendered as natural language ("midnight", "2am", "noon", "3pm") —
 * better for TTS than "02:00" and identical text feeds both UI and speech.
 * Early-morning precip peaks (00:00–04:59) always collapse to "overnight" —
 * the previous "only when no tie-in pins this hour" carve-out is gone now
 * that tie-in clauses no longer name a specific time. The spoken-time logic
 * itself is English-specific; the default impl falls back to 24h ASCII for
 * non-English locales until a dedicated formatter is added.
 */
class InsightFormatter(
    private val resources: Resources,
    private val locale: Locale = Locale.getDefault(),
) {
    private val phraser: ClothesPhraser = ClothesPhraser.forLocale(resources, locale)

    fun format(summary: InsightSummary): String = buildList {
        summary.alert?.let { add(formatAlert(it)) }
        add(formatBand(summary.period, summary.band))
        summary.delta?.let { add(formatDelta(it)) }
        summary.clothes?.let { add(formatClothes(it)) }
        summary.precip?.let { add(formatPrecip(it)) }
        // Both tie-in clauses share the item-only "Bring a X tonight." form;
        // the evening one additionally folds in the evening forecast's rain
        // time when it's ≥ 30%, since the morning's own precip clause only
        // covers the morning slice and otherwise wouldn't surface evening
        // rain. The clauses are separate fields because they're gated
        // differently (TONIGHT precip-peak overlap vs. TODAY evening-event
        // opt-in).
        summary.calendarTieIn?.let { add(formatTieIn(it.item)) }
        summary.eveningEventTieIn?.let { add(formatEveningEventTieIn(it)) }
    }.joinToString(" ")

    private fun formatAlert(alert: AlertClause): String =
        resources.getString(R.string.insight_alert, alert.event)

    private fun formatBand(period: ForecastPeriod, band: BandClause): String {
        val lead = resources.getString(leadRes(period))
        val low = resources.getString(bandRes(band.low))
        return if (band.low == band.high) {
            resources.getString(R.string.insight_band_single, lead, low)
        } else {
            val high = resources.getString(bandRes(band.high))
            resources.getString(R.string.insight_band_range, lead, low, high)
        }
    }

    private fun formatDelta(delta: DeltaClause): String {
        val template = when (delta.direction) {
            DeltaClause.Direction.WARMER -> R.string.insight_delta_warmer
            DeltaClause.Direction.COOLER -> R.string.insight_delta_cooler
        }
        return resources.getString(template, delta.degrees)
    }

    // TODO: "Wear an umbrella" reads awkwardly — umbrellas are carried, not worn.
    // Either drop umbrella from the clothes list (it's already implied by the
    // precip clause and the tonight calendar tie-in) or split the clause into
    // a "Wear …" sentence for garments and a "Bring …" sentence for accessories
    // like umbrella. Probably wants a small domain-side classification on
    // ClothesRule (item kind: garment / accessory) rather than hard-coding
    // umbrella here.
    private fun formatClothes(clothes: ClothesClause): String =
        resources.getString(R.string.insight_clothes_wear, phraser.joinItems(clothes.items))

    private fun formatPrecip(precip: PrecipClause): String {
        val type = resources.getString(conditionRes(precip.condition))
        // "Rain at 02:00" sounds robotic and a precise hour adds little value
        // when the user is asleep — collapse early-morning peaks to "overnight".
        val timePhrase = if (precip.time.hour in OVERNIGHT_HOURS) {
            resources.getString(R.string.insight_precip_overnight)
        } else {
            resources.getString(R.string.insight_precip_at_time, spokenTime(precip.time))
        }
        return resources.getString(R.string.insight_precip, type, timePhrase)
    }

    private fun formatTieIn(item: String): String =
        resources.getString(R.string.insight_tie_in, phraser.withArticle(item))

    private fun formatEveningEventTieIn(tieIn: EveningEventTieInClause): String {
        // Local capture so the null check enables smart cast — the property
        // lives on a :core:domain data class and Kotlin won't smart-cast a
        // public API property across modules.
        val rainTime = tieIn.rainTime ?: return formatTieIn(tieIn.item)
        return resources.getString(
            R.string.insight_tie_in_with_rain,
            phraser.withArticle(tieIn.item),
            spokenTime(rainTime),
        )
    }

    private fun leadRes(period: ForecastPeriod): Int = when (period) {
        ForecastPeriod.TODAY -> R.string.insight_lead_today
        ForecastPeriod.TONIGHT -> R.string.insight_lead_tonight
    }

    private fun bandRes(band: TemperatureBand): Int = when (band) {
        TemperatureBand.FREEZING -> R.string.insight_band_freezing
        TemperatureBand.COLD -> R.string.insight_band_cold
        TemperatureBand.COOL -> R.string.insight_band_cool
        TemperatureBand.MILD -> R.string.insight_band_mild
        TemperatureBand.WARM -> R.string.insight_band_warm
        TemperatureBand.HOT -> R.string.insight_band_hot
    }

    private fun conditionRes(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR -> R.string.insight_condition_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.insight_condition_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.insight_condition_cloudy
        WeatherCondition.FOG -> R.string.insight_condition_fog
        WeatherCondition.DRIZZLE -> R.string.insight_condition_drizzle
        WeatherCondition.RAIN -> R.string.insight_condition_rain
        WeatherCondition.SNOW -> R.string.insight_condition_snow
        WeatherCondition.THUNDERSTORM -> R.string.insight_condition_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.insight_condition_unknown
    }

    /**
     * 24h LocalTime → spoken English ("midnight", "2am", "noon", "3:30pm") for
     * `en-*` locales; falls back to 24h ASCII ("15:00") elsewhere until a
     * dedicated formatter is added. Locale.ROOT-formatted digits keep the
     * output ASCII regardless of device locale (Arabic locales would otherwise
     * shape any numeric minutes into Eastern Arabic numerals).
     */
    private fun spokenTime(time: LocalTime): String {
        if (locale.language != "en") {
            return "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)
        }
        val hour = time.hour
        val minute = time.minute
        if (hour == 0 && minute == 0) return resources.getString(R.string.insight_time_midnight)
        if (hour == 12 && minute == 0) return resources.getString(R.string.insight_time_noon)
        val h12 = ((hour + 11) % 12) + 1
        val template = when {
            hour < 12 && minute == 0 -> R.string.insight_time_am
            hour < 12 -> R.string.insight_time_am_minutes
            minute == 0 -> R.string.insight_time_pm
            else -> R.string.insight_time_pm_minutes
        }
        return if (minute == 0) resources.getString(template, h12)
        else resources.getString(template, h12, minute)
    }

    companion object {
        // Hours treated as "overnight" when collapsing precip times. 5am is borderline
        // morning rather than night, so the band stops at 4:59.
        private val OVERNIGHT_HOURS = 0..4

        /** Build a formatter that renders prose in [locale] using [context]'s resources. */
        fun forContext(context: Context, locale: Locale = Locale.getDefault()): InsightFormatter =
            InsightFormatter(context.localizedResources(locale), locale)

        /** Convenience for the common path: render in the user's [Region]-derived locale. */
        fun forRegion(context: Context, region: Region): InsightFormatter {
            val locale = region.toJavaLocale() ?: Locale.getDefault()
            return forContext(context, locale)
        }
    }
}

private fun Region.toJavaLocale(): Locale? = bcp47?.let { Locale.forLanguageTag(it) }

private fun Context.localizedResources(locale: Locale): Resources {
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config).resources
}
