package app.clothescast.core.domain.model

import java.time.LocalTime

/**
 * A glanceable two-piece outfit pairing — one [Top] and one [Bottom] — derived from the
 * forecast so the home screen can render two big icons instead of a comma-separated word
 * list. The set is intentionally tiny (3 tops × 2 bottoms) because the goal is "tell me
 * at a glance", not "build my closet". Customisable item-level rules still live in
 * [ClothesRule] and continue to drive [Insight.recommendedItems].
 *
 * Thresholds use feels-like temperatures (wind chill / humidity adjusted), matching what
 * [ClothesRule] does, since that's what people actually experience on the way out the
 * door.
 */
data class OutfitSuggestion(
    val top: Top,
    val bottom: Bottom,
) {
    enum class Top { TSHIRT, SWEATER, THICK_JACKET }

    enum class Bottom { SHORTS, LONG_PANTS }

    /**
     * The thresholds [fromForecast] uses to map a forecast onto a top + bottom. Pulled out
     * as a named object so both the suggestion and the rationale read the same numbers,
     * and so a future PR can swap [DEFAULT] for a user-tunable instance plumbed through
     * preferences without touching the call sites in [GenerateDailyInsight].
     */
    data class Thresholds(
        val sweaterMaxFeelsLikeMinC: Double,
        val tshirtMinFeelsLikeMinC: Double,
        val shortsMinFeelsLikeMaxC: Double,
        val shortsMinFeelsLikeMinC: Double,
    ) {
        companion object {
            val DEFAULT: Thresholds = Thresholds(
                sweaterMaxFeelsLikeMinC = 8.0,
                tshirtMinFeelsLikeMinC = 18.0,
                shortsMinFeelsLikeMaxC = 22.0,
                shortsMinFeelsLikeMinC = 15.0,
            )
        }
    }

    companion object {
        fun fromForecast(
            forecast: DailyForecast,
            thresholds: Thresholds = Thresholds.DEFAULT,
        ): OutfitSuggestion {
            val top = when {
                forecast.feelsLikeMinC < thresholds.sweaterMaxFeelsLikeMinC -> Top.THICK_JACKET
                forecast.feelsLikeMinC < thresholds.tshirtMinFeelsLikeMinC -> Top.SWEATER
                else -> Top.TSHIRT
            }
            // Shorts only when it's warm *and* doesn't drop too cold at the coldest part
            // of the day — nobody enjoys discovering at 7am that "shorts weather" was
            // really "afternoon shorts weather". Comparison is inclusive (`>=`) so the
            // "at least" prose in the rationale dialog reads true at the exact-boundary
            // edge case (max == 22°C is shorts weather, not long-pants).
            val bottom = if (
                forecast.feelsLikeMaxC >= thresholds.shortsMinFeelsLikeMaxC &&
                forecast.feelsLikeMinC >= thresholds.shortsMinFeelsLikeMinC
            ) {
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
         */
        fun explainFromForecast(
            forecast: DailyForecast,
            thresholds: Thresholds = Thresholds.DEFAULT,
        ): OutfitRationale {
            val coldestHour = forecast.hourly.minByOrNull { it.feelsLikeC }
            val warmestHour = forecast.hourly.maxByOrNull { it.feelsLikeC }
            val topFact = when {
                forecast.feelsLikeMinC < thresholds.sweaterMaxFeelsLikeMinC -> Fact(
                    metric = Fact.Metric.FEELS_LIKE_MIN,
                    observedC = forecast.feelsLikeMinC,
                    observedAt = coldestHour?.time,
                    thresholdC = thresholds.sweaterMaxFeelsLikeMinC,
                    comparison = Fact.Comparison.BELOW,
                )
                forecast.feelsLikeMinC < thresholds.tshirtMinFeelsLikeMinC -> Fact(
                    metric = Fact.Metric.FEELS_LIKE_MIN,
                    observedC = forecast.feelsLikeMinC,
                    observedAt = coldestHour?.time,
                    thresholdC = thresholds.tshirtMinFeelsLikeMinC,
                    comparison = Fact.Comparison.BELOW,
                )
                else -> Fact(
                    metric = Fact.Metric.FEELS_LIKE_MIN,
                    observedC = forecast.feelsLikeMinC,
                    observedAt = coldestHour?.time,
                    thresholdC = thresholds.tshirtMinFeelsLikeMinC,
                    comparison = Fact.Comparison.AT_OR_ABOVE,
                )
            }
            // Bottom is a two-condition rule (warm max AND not-too-cold min). For
            // SHORTS we name both supporting facts; for LONG_PANTS we name whichever
            // failed (or both, if both did) so the user sees the actual blocker.
            // Inclusive (`>=`) to match [fromForecast]'s operator, so the cached
            // [Fact.comparison] doesn't disagree with the live rule outcome at exact
            // equality.
            val maxOk = forecast.feelsLikeMaxC >= thresholds.shortsMinFeelsLikeMaxC
            val minOk = forecast.feelsLikeMinC >= thresholds.shortsMinFeelsLikeMinC
            val maxFact = Fact(
                metric = Fact.Metric.FEELS_LIKE_MAX,
                observedC = forecast.feelsLikeMaxC,
                observedAt = warmestHour?.time,
                thresholdC = thresholds.shortsMinFeelsLikeMaxC,
                comparison = if (maxOk) Fact.Comparison.AT_OR_ABOVE else Fact.Comparison.BELOW,
            )
            val minFact = Fact(
                metric = Fact.Metric.FEELS_LIKE_MIN,
                observedC = forecast.feelsLikeMinC,
                observedAt = coldestHour?.time,
                thresholdC = thresholds.shortsMinFeelsLikeMinC,
                comparison = if (minOk) Fact.Comparison.AT_OR_ABOVE else Fact.Comparison.BELOW,
            )
            val bottomFacts = when {
                maxOk && minOk -> listOf(maxFact, minFact)
                !maxOk && !minOk -> listOf(maxFact, minFact)
                !maxOk -> listOf(maxFact)
                else -> listOf(minFact)
            }
            return OutfitRationale(
                top = GarmentReason(facts = listOf(topFact)),
                bottom = GarmentReason(facts = bottomFacts),
            )
        }
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

/** Reasons for a single garment slot. Multiple [Fact]s when the rule has multiple terms. */
data class GarmentReason(
    val facts: List<Fact>,
)

/**
 * One observation-vs-threshold check. [observedAt] is null when the forecast was a
 * day-level aggregate without hourly entries (legacy fixtures, sparse caches) — the
 * UI omits the time clause in that case.
 */
data class Fact(
    val metric: Metric,
    val observedC: Double,
    val observedAt: LocalTime?,
    val thresholdC: Double,
    val comparison: Comparison,
) {
    enum class Metric { FEELS_LIKE_MIN, FEELS_LIKE_MAX }

    /** How [observedC] relates to [thresholdC]. */
    enum class Comparison { BELOW, AT_OR_ABOVE }
}
