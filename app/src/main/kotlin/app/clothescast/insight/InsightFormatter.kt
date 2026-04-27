package app.clothescast.insight

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import app.clothescast.R
import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.DeltaClause
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
 * Article picking (English "a jumper" / "an umbrella" / bare "shorts") is
 * locale-specific and dispatched via [ClothesPhraser.forLocale]. Languages
 * without an explicit phraser fall back to a no-article join.
 *
 * Times are rendered as natural language ("midnight", "2am", "noon", "3pm") —
 * better for TTS than "02:00" and identical text feeds both UI and speech.
 * Early-morning peaks (00:00–04:59) collapse to "overnight" when no calendar
 * event pins the hour. The spoken-time logic itself is English-specific; the
 * default impl falls back to 24h ASCII for non-English locales until a
 * dedicated formatter is added.
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
        summary.precip?.let { add(formatPrecip(it, hasCalendarTieIn = summary.calendarTieIn != null)) }
        summary.calendarTieIn?.let { add(formatCalendarTieIn(it)) }
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

    private fun formatClothes(clothes: ClothesClause): String =
        resources.getString(R.string.insight_clothes_wear, phraser.joinItems(clothes.items))

    private fun formatPrecip(precip: PrecipClause, hasCalendarTieIn: Boolean): String {
        val type = resources.getString(conditionRes(precip.condition))
        // "Rain at 02:00" sounds robotic and a precise hour adds little value when
        // the user is asleep. Collapse early-morning peaks to "overnight" — but only
        // when there's no calendar tie-in pinning the time, since that clause names
        // the same hour and the two should agree.
        val timePhrase = if (precip.time.hour in OVERNIGHT_HOURS && !hasCalendarTieIn) {
            resources.getString(R.string.insight_precip_overnight)
        } else {
            resources.getString(R.string.insight_precip_at_time, spokenTime(precip.time))
        }
        return resources.getString(R.string.insight_precip, type, timePhrase)
    }

    private fun formatCalendarTieIn(tieIn: CalendarTieInClause): String =
        resources.getString(
            R.string.insight_calendar_tie_in,
            phraser.withArticle(tieIn.item),
            spokenTime(tieIn.time),
            tieIn.title,
        )

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
