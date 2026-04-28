package app.clothescast.insight

import android.content.res.Resources
import app.clothescast.R
import app.clothescast.core.domain.model.Garment
import java.util.Locale

/**
 * Joins a list of clothes items into a single comma/and-separated phrase
 * suitable for the "Wear …" sentence and the calendar tie-in. Article picking
 * is grammar-specific (English needs "a" / "an" only on the first item; German
 * articles depend on grammatical gender; languages with no articles need
 * nothing) so each language plugs in its own [ClothesPhraser]. Unknown locales
 * fall through to [ResourceClothesPhraser], which translates known garment keys
 * via the current locale's `garment_*` string resources and joins with the
 * locale's `insight_clothes_join_*` templates.
 */
internal interface ClothesPhraser {
    /** Article-prefixed form of a single item, used by the calendar tie-in clause. */
    fun withArticle(item: String): String

    /** Join multiple items into the body of a "Wear …" sentence. */
    fun joinItems(items: List<String>): String

    companion object {
        fun forLocale(resources: Resources, locale: Locale): ClothesPhraser =
            when (locale.language) {
                "en" -> EnglishClothesPhraser(resources)
                "de" -> GermanClothesPhraser(resources)
                else -> ResourceClothesPhraser(resources)
            }
    }
}

/**
 * English article picker.
 *  - Items ending in 's' are treated as plural (shorts, boots, gloves) and take
 *    no article.
 *  - Items starting with a vowel letter take "an"; everything else takes "a".
 * Subsequent items in the list are emitted bare per the user-preferred phrasing
 * "Wear a sweater and jacket." rather than fully grammatical "a sweater and a jacket."
 */
internal class EnglishClothesPhraser(private val resources: Resources) : ClothesPhraser {
    override fun withArticle(item: String): String = when {
        item.endsWith("s", ignoreCase = true) -> item
        item.firstOrNull()?.let { it.lowercaseChar() in "aeiou" } == true ->
            resources.getString(R.string.insight_clothes_article_an, item)
        else -> resources.getString(R.string.insight_clothes_article_a, item)
    }

    override fun joinItems(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> withArticle(items[0])
        2 -> resources.getString(R.string.insight_clothes_join_two, withArticle(items[0]), items[1])
        else -> resources.getString(
            R.string.insight_clothes_join_many,
            withArticle(items[0]),
            items.subList(1, items.size - 1).joinToString(", "),
            items.last(),
        )
    }
}

/**
 * German list join: items are emitted bare because we don't carry grammatical
 * gender on each clothes rule (der/die/das ⇒ einen/eine/ein for "Trag …"), so
 * any guessed article is wrong half the time. Items are joined with commas
 * and a final "und"; no Oxford comma. The calendar tie-in ("Denk an …")
 * likewise emits the item bare.
 *
 * Items are passed through [translate] first so the default English-keyed
 * `ClothesRule` set ("sweater", "jacket", "shorts", "umbrella") doesn't leak
 * into otherwise-German prose ("Trag sweater und jacket." → "Trag Pullover
 * und Jacke."). Items not in the table fall through unchanged — better to
 * echo the source key than guess wrong.
 *
 * A future PR could thread a gender hint through `ClothesRule` so we can
 * pick a real article — until then, "Trag Pullover, Jacke und Regenschirm."
 * reads naturally enough.
 */
internal class GermanClothesPhraser(private val resources: Resources) : ClothesPhraser {
    override fun withArticle(item: String): String = translate(item)

    override fun joinItems(items: List<String>): String {
        val translated = items.map(::translate)
        return when (translated.size) {
            0 -> ""
            1 -> translated[0]
            2 -> resources.getString(R.string.insight_clothes_join_two, translated[0], translated[1])
            else -> resources.getString(
                R.string.insight_clothes_join_many,
                translated[0],
                translated.subList(1, translated.size - 1).joinToString(", "),
                translated.last(),
            )
        }
    }

    /**
     * Maps the most common English clothes / weather-gear keywords to their
     * German equivalents. Lookup is case-insensitive; output preserves German
     * capitalization (German nouns are always capitalised). Anything not in
     * the table falls through unchanged.
     */
    private fun translate(item: String): String =
        EN_TO_DE[item.trim().lowercase(Locale.ROOT)] ?: item

    private companion object {
        // Defaults shipped in ClothesRule.DEFAULTS plus the obvious extras a user
        // is likely to add (coat, scarf, gloves, hat, boots …). Kept tight rather
        // than exhaustive — adding more is a one-line change. The canonical
        // source key for the cold-weather top is "sweater" (matches the
        // today_outfit_top_sweater label); en-GB / en-AU say "Jumper" only as
        // a display override on that label, never as a stored item key.
        private val EN_TO_DE: Map<String, String> = mapOf(
            "sweater" to "Pullover",
            "hoodie" to "Hoodie",
            "jacket" to "Jacke",
            "coat" to "Mantel",
            "raincoat" to "Regenjacke",
            "shirt" to "Hemd",
            "t-shirt" to "T-Shirt",
            "tshirt" to "T-Shirt",
            "shorts" to "kurze Hose",
            // Lowercase "kurze" / "lange" because the prose embeds them
            // mid-sentence ("Trag kurze Hose und Pullover."); only the noun
            // "Hose" stays capitalised. The dropdown labels in
            // values-de/strings.xml (garment_*) use sentence-style capitals
            // since they stand alone as a UI label.
            "pants" to "lange Hose",
            "trousers" to "lange Hose",
            "jeans" to "Jeans",
            "umbrella" to "Regenschirm",
            "hat" to "Hut",
            "cap" to "Kappe",
            "beanie" to "Mütze",
            "scarf" to "Schal",
            "gloves" to "Handschuhe",
            "mittens" to "Fäustlinge",
            "boots" to "Stiefel",
            "socks" to "Socken",
            "sunglasses" to "Sonnenbrille",
            "sunscreen" to "Sonnencreme",
        )
    }
}

/**
 * Generic phraser for locales with no complex article grammar (Chinese, Hindi,
 * Japanese, Korean, French, Spanish, etc.). Translates known garment keys via
 * the current locale's `garment_*` Android string resources, applies the
 * locale's `insight_clothes_article_a` template for [withArticle] (a no-op
 * passthrough `%1$s` for languages without articles), and joins using the
 * locale's `insight_clothes_join_*` templates.
 *
 * Unknown garment keys fall through unchanged — better to echo the source key
 * than guess wrong. Replaces [BareClothesPhraser] in [ClothesPhraser.forLocale]
 * so all non-en/de locales get translated garment names and locale-correct list
 * punctuation rather than raw English keys comma-joined.
 */
internal class ResourceClothesPhraser(private val resources: Resources) : ClothesPhraser {
    override fun withArticle(item: String): String =
        resources.getString(R.string.insight_clothes_article_a, translate(item))

    override fun joinItems(items: List<String>): String {
        val translated = items.map(::translate)
        return when (translated.size) {
            0 -> ""
            1 -> translated[0]
            2 -> resources.getString(R.string.insight_clothes_join_two, translated[0], translated[1])
            else -> resources.getString(
                R.string.insight_clothes_join_many,
                translated[0],
                translated.subList(1, translated.size - 1).joinToString(", "),
                translated.last(),
            )
        }
    }

    private fun translate(item: String): String {
        val garment = Garment.fromKey(item) ?: return item
        val resId = GARMENT_RES_IDS[garment] ?: return item
        return resources.getString(resId)
    }

    private companion object {
        val GARMENT_RES_IDS: Map<Garment, Int> = mapOf(
            Garment.SWEATER to R.string.garment_sweater,
            Garment.HOODIE to R.string.garment_hoodie,
            Garment.JACKET to R.string.garment_jacket,
            Garment.COAT to R.string.garment_coat,
            Garment.TSHIRT to R.string.garment_tshirt,
            Garment.SHIRT to R.string.garment_shirt,
            Garment.SHORTS to R.string.garment_shorts,
            Garment.PANTS to R.string.garment_pants,
            Garment.JEANS to R.string.garment_jeans,
        )
    }
}

/**
 * Bare comma-join with no translation. Used in tests; not returned from
 * [ClothesPhraser.forLocale] — [ResourceClothesPhraser] handles the generic
 * case there.
 */
internal class BareClothesPhraser : ClothesPhraser {
    override fun withArticle(item: String): String = item

    override fun joinItems(items: List<String>): String = items.joinToString(", ")
}
