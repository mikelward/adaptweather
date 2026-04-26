package app.clothescast.core.domain.model

/**
 * A glanceable two-piece outfit pairing — one [Top] and one [Bottom] — derived from the
 * forecast so the home screen can render two big icons instead of a comma-separated word
 * list. The set is intentionally tiny (3 tops × 2 bottoms) because the goal is "tell me
 * at a glance", not "build my closet". Customisable item-level rules still live in
 * [WardrobeRule] and continue to drive [Insight.recommendedItems].
 *
 * Thresholds use feels-like temperatures (wind chill / humidity adjusted), matching what
 * [WardrobeRule] does, since that's what people actually experience on the way out the
 * door.
 */
data class OutfitSuggestion(
    val top: Top,
    val bottom: Bottom,
) {
    enum class Top { TSHIRT, SWEATER, THICK_JACKET }

    enum class Bottom { SHORTS, LONG_PANTS }

    companion object {
        fun fromForecast(forecast: DailyForecast): OutfitSuggestion {
            val top = when {
                forecast.feelsLikeMinC < 8.0 -> Top.THICK_JACKET
                forecast.feelsLikeMinC < 18.0 -> Top.SWEATER
                else -> Top.TSHIRT
            }
            // Shorts only when it's warm *and* doesn't drop too cold at the coldest part
            // of the day — nobody enjoys discovering at 7am that "shorts weather" was
            // really "afternoon shorts weather".
            val bottom = if (forecast.feelsLikeMaxC > 22.0 && forecast.feelsLikeMinC > 15.0) {
                Bottom.SHORTS
            } else {
                Bottom.LONG_PANTS
            }
            return OutfitSuggestion(top, bottom)
        }
    }
}
