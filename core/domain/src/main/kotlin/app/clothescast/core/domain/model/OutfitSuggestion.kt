package app.clothescast.core.domain.model

import java.time.LocalTime

/**
 * A glanceable two-piece outfit pairing — one [Top] and one [Bottom] — derived from the
 * forecast so the home screen can render two big icons instead of a comma-separated word
 * list. The set is intentionally tiny (3 tops × 2 bottoms) because the goal is "tell me
 * at a glance", not "build my closet".
 *
 * The picker is driven entirely by the user's [ClothesRule] list — the same rules that
 * populate [Insight.recommendedItems]. Specifically: a firing `jacket`/`coat` rule
 * promotes the top to [Top.THICK_JACKET], a firing `sweater`/`hoodie` rule promotes it
 * to [Top.SWEATER], and a firing `shorts` rule swaps the bottom to [Bottom.SHORTS].
 * That keeps the home-screen icon and the bulleted recommendations honest about the
 * same set of cutoffs — no second mental model, no shadow defaults.
 *
 * Rule conditions are checked against feels-like temperatures (wind chill / humidity
 * adjusted) — that's what people experience on the way out the door, and it's already
 * what [ClothesRule.appliesTo] does.
 */
data class OutfitSuggestion(
    val top: Top,
    val bottom: Bottom,
) {
    enum class Top { TSHIRT, SWEATER, THICK_JACKET }

    enum class Bottom { SHORTS, LONG_PANTS }

    companion object {
        // Catalog item keys (see [Garment.itemKey]) that drive each icon tier.
        // The top tiers are layered: any firing `jacket` / `coat` rule promotes
        // the icon to THICK_JACKET, otherwise a firing `sweater` / `hoodie` lands
        // on SWEATER. Order within each list also drives which rule the
        // rationale cites when multiple fire — the canonical key (`jacket` /
        // `sweater`) comes first because that's the icon-boundary the user
        // crossed. A `coat` rule that fires alongside `jacket` only gets
        // surfaced when `jacket` is missing from the user's list. A user who
        // deleted everything in the cold half gets TSHIRT for any temperature.
        private val THICK_JACKET_KEYS = listOf("jacket", "coat")
        private val SWEATER_KEYS = listOf("sweater", "hoodie")
        private val SHORTS_KEYS = listOf("shorts")

        fun fromForecast(
            forecast: DailyForecast,
            clothesRules: List<ClothesRule>,
        ): OutfitSuggestion {
            val top = when {
                clothesRules.firstFiring(forecast, THICK_JACKET_KEYS) != null -> Top.THICK_JACKET
                clothesRules.firstFiring(forecast, SWEATER_KEYS) != null -> Top.SWEATER
                else -> Top.TSHIRT
            }
            val bottom = if (clothesRules.firstFiring(forecast, SHORTS_KEYS) != null) {
                Bottom.SHORTS
            } else {
                Bottom.LONG_PANTS
            }
            return OutfitSuggestion(top, bottom)
        }

        /**
         * Returns the human-readable reasons that [fromForecast] picked this outfit —
         * one [GarmentReason] for the top slot and one for the bottom. The "Why this
         * outfit?" sheet on the home screen uses this to surface the deciding numbers
         * (and the time of the deciding hour) so the user can sanity-check the call.
         *
         * Each fact's [Fact.ruleItem] points at the `ClothesRule.item` key that
         * supplied the threshold — the rationale dialog uses that to wire its
         * `−1° / +1°` controls back to the right rule.
         */
        fun explainFromForecast(
            forecast: DailyForecast,
            clothesRules: List<ClothesRule>,
        ): OutfitRationale {
            val coldestHour = forecast.hourly.minByOrNull { it.feelsLikeC }?.time
            val warmestHour = forecast.hourly.maxByOrNull { it.feelsLikeC }?.time
            return OutfitRationale(
                top = GarmentReason(facts = listOf(topFact(forecast, clothesRules, coldestHour))),
                bottom = GarmentReason(facts = listOf(bottomFact(forecast, clothesRules, warmestHour))),
            )
        }

        private fun topFact(
            forecast: DailyForecast,
            rules: List<ClothesRule>,
            coldestHour: LocalTime?,
        ): Fact {
            // The deciding rule, in priority order: the firing thick-jacket rule;
            // otherwise the firing sweater rule; otherwise the warmest cold rule
            // we *aren't* below (the "above the sweater cutoff" case for TSHIRT).
            // The default sweater rule is the last-resort anchor so we always have
            // something to cite, even if the user deleted every cold rule.
            val rule = rules.firstFiring(forecast, THICK_JACKET_KEYS)
                ?: rules.firstFiring(forecast, SWEATER_KEYS)
                ?: rules.firstByKey(SWEATER_KEYS)
                ?: rules.firstByKey(THICK_JACKET_KEYS)
                ?: ClothesRule.DEFAULTS.first { it.item == "sweater" }
            return rule.toMinFact(forecast, coldestHour)
        }

        private fun bottomFact(
            forecast: DailyForecast,
            rules: List<ClothesRule>,
            warmestHour: LocalTime?,
        ): Fact {
            // Same idea on the warm side: a firing shorts rule wins; otherwise we
            // cite whichever shorts rule the user has on file (or the catalog
            // default) so the dialog can still show the cutoff that wasn't met.
            val rule = rules.firstFiring(forecast, SHORTS_KEYS)
                ?: rules.firstByKey(SHORTS_KEYS)
                ?: ClothesRule.DEFAULTS.first { it.item == "shorts" }
            return rule.toMaxFact(forecast, warmestHour)
        }

        private fun List<ClothesRule>.firstFiring(
            forecast: DailyForecast,
            keys: List<String>,
        ): ClothesRule? = keys.firstNotNullOfOrNull { key ->
            firstOrNull { it.item == key && it.appliesTo(forecast) }
        }

        private fun List<ClothesRule>.firstByKey(keys: List<String>): ClothesRule? =
            keys.firstNotNullOfOrNull { key -> firstOrNull { it.item == key } }

        private fun ClothesRule.toFact(
            metric: Fact.Metric,
            observedC: Double,
            observedAt: LocalTime?,
        ): Fact {
            val thresholdC = thresholdC()
                ?: error("Outfit rationale only supports temperature rules; got $condition")
            return Fact(
                metric = metric,
                observedC = observedC,
                observedAt = observedAt,
                thresholdC = thresholdC,
                ruleItem = item,
                comparison = if (observedC < thresholdC) {
                    Fact.Comparison.BELOW
                } else {
                    Fact.Comparison.AT_OR_ABOVE
                },
            )
        }

        private fun ClothesRule.toMinFact(forecast: DailyForecast, observedAt: LocalTime?): Fact =
            toFact(Fact.Metric.FEELS_LIKE_MIN, forecast.feelsLikeMinC, observedAt)

        private fun ClothesRule.toMaxFact(forecast: DailyForecast, observedAt: LocalTime?): Fact =
            toFact(Fact.Metric.FEELS_LIKE_MAX, forecast.feelsLikeMaxC, observedAt)
    }
}

/**
 * Why a particular [OutfitSuggestion] was picked. The UI renders this as bulleted text
 * under the garment icons on the "Why this outfit?" sheet.
 */
data class OutfitRationale(
    val top: GarmentReason,
    val bottom: GarmentReason,
)

/** Reasons for a single garment slot. One [Fact] per slot in the current picker. */
data class GarmentReason(
    val facts: List<Fact>,
)

/**
 * One observation-vs-threshold check. [observedAt] is null when the forecast was a
 * day-level aggregate without hourly entries (legacy fixtures, sparse caches) — the
 * UI omits the time clause in that case.
 *
 * [ruleItem] names *which* [ClothesRule] this fact came from (by its `item` key), so
 * the rationale dialog can wire its `−1°` / `+1°` buttons back to the same rule and
 * persist user adjustments to the right entry on disk.
 */
data class Fact(
    val metric: Metric,
    val observedC: Double,
    val observedAt: LocalTime?,
    val thresholdC: Double,
    val ruleItem: String,
    val comparison: Comparison,
) {
    enum class Metric { FEELS_LIKE_MIN, FEELS_LIKE_MAX }

    /** How [observedC] relates to [thresholdC]. */
    enum class Comparison { BELOW, AT_OR_ABOVE }
}
