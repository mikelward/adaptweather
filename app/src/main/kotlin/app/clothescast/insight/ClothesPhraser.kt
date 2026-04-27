package app.clothescast.insight

import android.content.res.Resources
import app.clothescast.R
import java.util.Locale

/**
 * Joins a list of clothes items into a single comma/and-separated phrase
 * suitable for the "Wear …" sentence and the calendar tie-in. Article picking
 * is grammar-specific (English needs "a" / "an" only on the first item; German
 * articles depend on grammatical gender; languages with no articles need
 * nothing) so each language plugs in its own [ClothesPhraser]. Unknown locales
 * fall through to [BareClothesPhraser], which just lists items without articles.
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
                else -> BareClothesPhraser()
            }
    }
}

/**
 * English article picker.
 *  - Items ending in 's' are treated as plural (shorts, boots, gloves) and take
 *    no article.
 *  - Items starting with a vowel letter take "an"; everything else takes "a".
 * Subsequent items in the list are emitted bare per the user-preferred phrasing
 * "Wear a jumper and jacket." rather than fully grammatical "a jumper and a jacket."
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
 * A future PR could thread a gender hint through `ClothesRule` so we can
 * pick a real article — until then, "Trag Pullover, Jacke und Regenschirm."
 * reads naturally enough.
 */
internal class GermanClothesPhraser(private val resources: Resources) : ClothesPhraser {
    override fun withArticle(item: String): String = item

    override fun joinItems(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> resources.getString(R.string.insight_clothes_join_two, items[0], items[1])
        else -> resources.getString(
            R.string.insight_clothes_join_many,
            items[0],
            items.subList(1, items.size - 1).joinToString(", "),
            items.last(),
        )
    }
}

/**
 * Fallback for locales without a dedicated phraser. Lists items as a comma /
 * and-joined sequence with no articles. Good enough for a first-pass
 * translation — a language-specific phraser can override later.
 */
internal class BareClothesPhraser : ClothesPhraser {
    override fun withArticle(item: String): String = item

    override fun joinItems(items: List<String>): String = items.joinToString(", ")
}
