package app.adaptweather.core.domain.usecase

import app.adaptweather.core.domain.model.AlertSeverity
import app.adaptweather.core.domain.model.DailyForecast
import app.adaptweather.core.domain.model.WardrobeRule
import app.adaptweather.core.domain.model.WeatherAlert
import app.adaptweather.core.domain.model.WeatherCondition
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders the daily insight summary as a deterministic single string of up to five
 * short sentences, each driven by an independent rule. Replaces the previous
 * Gemini call: every sentence is template-fillable from the forecast, so the LLM
 * round trip wasn't earning its keep.
 *
 * Rules (each yields 0 or 1 sentence, joined with single spaces):
 * 1. Severe alert: highest-severity SEVERE/EXTREME → "Alert: <event>." Extreme
 *    outranks Severe; ties take the first listed.
 * 2. Temperature band: classify today's feels-like low and high into bands. Output
 *    "Today will be <band>." (same band) or "Today will be <coldband> to <hotband>."
 *    (different bands). Always emitted.
 * 3. Temperature change vs yesterday: if max OR min feels-like differs by ≥ 3°,
 *    output "It will be N° warmer/cooler today." using the larger absolute delta.
 * 4. Wardrobe: if any wardrobe rule triggers today, output "Wear <items>." with
 *    an article on the first item ("a" / "an" / no article for plurals) and
 *    " and " before the last item (Oxford comma for three or more).
 * 5. Precipitation: peak chance ≥ 30% → "<Type> at <HH:MM>."
 *
 * All temperature comparisons use feels-like values, matching the wardrobe rules.
 */
class RenderInsightSummary {
    operator fun invoke(
        today: DailyForecast,
        yesterday: DailyForecast,
        todayTriggeredRules: List<WardrobeRule>,
        alerts: List<WeatherAlert> = emptyList(),
    ): String = buildList {
        alertSentence(alerts)?.let { add(it) }
        add(bandSentence(today))
        deltaSentence(today, yesterday)?.let { add(it) }
        wardrobeSentence(todayTriggeredRules.map { it.item })?.let { add(it) }
        precipSentence(today)?.let { add(it) }
    }.joinToString(" ")

    private fun alertSentence(alerts: List<WeatherAlert>): String? {
        val top = alerts.firstOrNull { it.severity == AlertSeverity.EXTREME }
            ?: alerts.firstOrNull { it.severity == AlertSeverity.SEVERE }
            ?: return null
        return "Alert: ${top.event}."
    }

    private fun bandSentence(today: DailyForecast): String {
        val low = TemperatureBand.forCelsius(today.feelsLikeMinC)
        val high = TemperatureBand.forCelsius(today.feelsLikeMaxC)
        val label = if (low == high) low.label else "${low.label} to ${high.label}"
        return "Today will be $label."
    }

    private fun deltaSentence(today: DailyForecast, yesterday: DailyForecast): String? {
        val highDelta = today.feelsLikeMaxC - yesterday.feelsLikeMaxC
        val lowDelta = today.feelsLikeMinC - yesterday.feelsLikeMinC
        val biggest = if (abs(highDelta) >= abs(lowDelta)) highDelta else lowDelta
        // Apply the threshold against the *unrounded* delta. Otherwise 2.6°C rounds
        // to 3 and would emit a sentence even though the actual delta is under the
        // 3° rule.
        if (abs(biggest) < 3.0) return null
        val rounded = biggest.roundToInt()
        val direction = if (rounded > 0) "warmer" else "cooler"
        return "It will be ${abs(rounded)}° $direction today."
    }

    private fun wardrobeSentence(items: List<String>): String? {
        if (items.isEmpty()) return null
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

    private fun precipSentence(today: DailyForecast): String? {
        val peak = today.hourly.maxByOrNull { it.precipitationProbabilityPct }
        val time: LocalTime
        val condition: WeatherCondition
        if (peak == null || peak.precipitationProbabilityPct < 30.0) {
            if (today.precipitationProbabilityMaxPct < 30.0) return null
            time = LocalTime.NOON
            condition = today.condition
        } else {
            time = peak.time
            condition = if (peak.condition == WeatherCondition.UNKNOWN) today.condition else peak.condition
        }
        val type = condition.name.lowercase(Locale.ROOT).replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return "$type at $time."
    }
}

/**
 * Bands classify a feels-like temperature (°C) into a glanceable label. Boundaries
 * are inclusive at the lower edge: 12.0°C is "cool", 11.9°C is "cold".
 */
internal enum class TemperatureBand(val label: String) {
    FREEZING("freezing"),
    COLD("cold"),
    COOL("cool"),
    MILD("mild"),
    WARM("warm"),
    HOT("hot");

    companion object {
        fun forCelsius(c: Double): TemperatureBand = when {
            c < 4.0 -> FREEZING
            c < 12.0 -> COLD
            c < 18.0 -> COOL
            c < 24.0 -> MILD
            c < 28.0 -> WARM
            else -> HOT
        }
    }
}
