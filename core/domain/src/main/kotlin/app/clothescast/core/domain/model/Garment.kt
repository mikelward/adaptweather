package app.clothescast.core.domain.model

import java.util.Locale

/**
 * Catalog of garments the user can pick from when adding or editing a
 * [ClothesRule] in Settings. Each entry has a stable, en-US-flavoured
 * [itemKey] that the rule's `item` field stores — so a German user with the
 * "Sweater" rule still has `item = "sweater"` on disk, and the German phraser
 * translates to "Pullover" at render time.
 *
 * The catalog is intentionally finite: free-form garment names defeat
 * translation (the German phraser can't translate arbitrary user input), so
 * the editor UI only lets the user pick from this list. Today the list covers
 * tops and bottoms — headwear, accessories, and rain / sun gear can land in a
 * follow-up PR (and will likely come paired with their own outfit-card icons).
 *
 * Stays in `:core:domain` so the rule-evaluation tests and the future
 * `ClothesRule.item` migration to a typed field can reach it without pulling
 * Android in. Localised display labels live in `app/src/main/res/values/`
 * (and the per-locale `values-de/`, `values-en-rGB/`, `values-en-rAU/`);
 * `:app` owns the enum→resource mapping. Resource names are an
 * implementation detail there — they don't always mirror [itemKey] directly
 * (e.g. TSHIRT's "t-shirt" key is exposed as `garment_tshirt`, since hyphens
 * are illegal in Android resource names).
 */
enum class Garment(val itemKey: String) {
    // Tops, coldest-leaning first. Sweater + jacket are shipped as defaults.
    SWEATER("sweater"),
    HOODIE("hoodie"),
    JACKET("jacket"),
    COAT("coat"),
    TSHIRT("t-shirt"),
    SHIRT("shirt"),

    // Bottoms. Shorts is shipped as a default; en-GB renders "pants" as "Trousers".
    SHORTS("shorts"),
    PANTS("pants"),
    JEANS("jeans");

    companion object {
        /**
         * Resolves a stored `ClothesRule.item` string back to a [Garment].
         * Tolerates a few common spelling variants ("tshirt", "trousers",
         * "jumper") so older rule data and any free-form items the user typed
         * before the catalog landed still map cleanly. Returns `null` for
         * anything else — callers that need to display unknown items should
         * fall back to the raw string.
         */
        fun fromKey(key: String): Garment? {
            val normalized = key.trim().lowercase(Locale.ROOT)
            entries.firstOrNull { it.itemKey == normalized }?.let { return it }
            return when (normalized) {
                "tshirt" -> TSHIRT
                "trousers", "long pants" -> PANTS
                "jumper" -> SWEATER
                else -> null
            }
        }
    }
}
