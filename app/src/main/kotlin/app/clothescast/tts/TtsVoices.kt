package app.clothescast.tts

import app.clothescast.core.data.tts.ElevenLabsVoiceSummary
import app.clothescast.core.domain.model.VoiceLocale

/**
 * Curated voice lists for the user-facing voice picker. Each provider has many more
 * options than this — these are the ones surfaced in Settings to keep the picker
 * scannable.
 *
 * The [id] is what's persisted and sent to the API. The [displayName] is shown in the
 * dropdown and is intentionally English-only for v1; if/when we localize, these move
 * to strings.xml.
 *
 * [locale] is the voice's *baked-in* accent — only meaningful for providers whose
 * voices have a fixed accent that can't be reliably steered by prompt. Both
 * ElevenLabs voice clones and OpenAI's `gpt-4o-mini-tts` voices fall into this
 * bucket: OpenAI's `instructions` field documents accent as steerable but in
 * practice the voice's baked-in timbre dominates (we tried both vague and
 * linguist-standard phrasings — "British English accent" and "Standard
 * Southern British accent" — and neither shifts `nova` off American). Null
 * means the picker does not filter that voice by accent — Gemini's prebuilt
 * voices are language-agnostic personalities and reliably follow accent
 * direction in the prompt. The picker only filters by [locale] when it's
 * non-null.
 */
data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val locale: VoiceLocale? = null,
)

/**
 * Filters [voices] to the user's preferred [variant], with a tiered fallback
 * so the picker is never empty *and* near-matches are preferred over a
 * language-mismatched dump:
 *
 *  1. **Exact**: voices whose [TtsVoiceOption.locale] matches [variant].
 *  2. **Language**: if (1) is empty, voices whose locale shares the BCP-47
 *     language subtag with [variant] — e.g. an en-AU user with no AU voice
 *     in the list still gets the en-US + en-GB voices, not Japanese ones.
 *  3. **Full list**: if (2) is also empty, return everything so the user has
 *     *some* picker. The UI surfaces a "try another accent" caption in this
 *     case via [localeFallbackTier].
 *
 * Voices with a `null` locale are accent-agnostic (Gemini's prebuilt voices,
 * or ElevenLabs voices whose `accent` label didn't parse): they always ride
 * along with whichever tier wins, but they don't drive tier selection. This
 * matters for refreshed ElevenLabs lists that mix known-accent voices with
 * unparseable ones — without this distinction a single unknown-accent voice
 * would suppress the language-fallback tier and hide the caption.
 *
 * [VoiceLocale.SYSTEM] disables filtering — the user has not expressed a
 * preference, so show everything.
 *
 * If [keepSelected] is supplied and points to a voice in the receiver list
 * that doesn't match the chosen tier, that voice is appended so the picker
 * still shows the user's current selection. Without this, a user who'd
 * persisted `nova` and then switched the variant to en-GB would see a
 * picker label of "Voice: nova" with no `nova` row in the dialog —
 * confusing UX. Pass `null` (the default) when this preservation isn't
 * needed.
 */
fun List<TtsVoiceOption>.filterByVariant(
    variant: VoiceLocale,
    keepSelected: String? = null,
): List<TtsVoiceOption> {
    if (variant == VoiceLocale.SYSTEM) return this
    val agnostic = filter { it.locale == null }
    val withLocale = filter { it.locale != null }
    val exact = withLocale.filter { it.locale == variant }
    if (exact.isNotEmpty()) return appendKeepSelected(agnostic + exact, keepSelected)
    val variantLang = variant.languageSubtag()
    if (variantLang != null) {
        val sameLanguage = withLocale.filter { it.locale?.languageSubtag() == variantLang }
        if (sameLanguage.isNotEmpty()) return appendKeepSelected(agnostic + sameLanguage, keepSelected)
    }
    return this
}

/**
 * Which tier [filterByVariant] landed on for [variant]. The picker UI uses
 * this to caption the picker with the right explanation when we couldn't
 * give the user their exact accent.
 *
 * Tier detection considers only voices whose locale is known (non-null).
 * An all-agnostic source — Gemini's prebuilt voices, or a refreshed
 * ElevenLabs library where every voice's accent label was unparseable —
 * is treated as Exact (no caption), since by construction we can't tell
 * the user a more useful story than "showing everything".
 */
enum class LocaleFallbackTier {
    /** Engine has voices in this exact accent. No caption needed. */
    Exact,

    /** No exact-accent match, but at least one same-language voice exists. */
    SameLanguage,

    /** No same-language match either — picker is showing the full list. */
    FullList,
}

fun List<TtsVoiceOption>.localeFallbackTier(variant: VoiceLocale): LocaleFallbackTier {
    if (variant == VoiceLocale.SYSTEM) return LocaleFallbackTier.Exact
    // All-agnostic (or empty) source: there's no accent claim to fall back
    // *from*, so don't try to caption one. Gemini's voice list lands here
    // by design; an ElevenLabs library where every voice failed accent-
    // parsing also lands here, since we can't credibly say "no <locale>
    // voices" when we don't know any voice's accent.
    if (none { it.locale != null }) return LocaleFallbackTier.Exact
    if (any { it.locale == variant }) return LocaleFallbackTier.Exact
    val variantLang = variant.languageSubtag() ?: return LocaleFallbackTier.FullList
    if (any { it.locale?.languageSubtag() == variantLang }) return LocaleFallbackTier.SameLanguage
    return LocaleFallbackTier.FullList
}

private fun List<TtsVoiceOption>.appendKeepSelected(
    matched: List<TtsVoiceOption>,
    keepSelectedId: String?,
): List<TtsVoiceOption> {
    val selected = keepSelectedId?.let { id -> firstOrNull { it.id == id } }
    return if (selected != null && selected !in matched) matched + selected else matched
}

// "en-GB" → "en"; "sr-Latn-RS" → "sr"; null → null. ICU/BCP-47 always puts
// the language tag first.
private fun VoiceLocale.languageSubtag(): String? = bcp47?.substringBefore('-')

val GEMINI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("Kore", "Kore — Firm"),
    TtsVoiceOption("Charon", "Charon — Informative"),
    TtsVoiceOption("Sulafat", "Sulafat — Warm"),
    TtsVoiceOption("Achird", "Achird — Friendly"),
    TtsVoiceOption("Despina", "Despina — Smooth"),
    TtsVoiceOption("Vindemiatrix", "Vindemiatrix — Gentle"),
    TtsVoiceOption("Iapetus", "Iapetus — Clear"),
    TtsVoiceOption("Algieba", "Algieba — Smooth"),
    TtsVoiceOption("Erinome", "Erinome — Clear"),
    TtsVoiceOption("Orus", "Orus — Firm"),
    TtsVoiceOption("Aoede", "Aoede — Breezy"),
    TtsVoiceOption("Leda", "Leda — Youthful"),
    TtsVoiceOption("Zephyr", "Zephyr — Bright"),
    TtsVoiceOption("Puck", "Puck — Upbeat"),
    TtsVoiceOption("Fenrir", "Fenrir — Excitable"),
    TtsVoiceOption("Sadaltager", "Sadaltager — Knowledgeable"),
)

// OpenAI's stock voices have *baked-in* accents. The `gpt-4o-mini-tts` model
// documents `instructions` as accepting an accent direction, but field-testing
// confirmed it doesn't shift the voice off its native accent — only `fable`
// (the one British voice in the stock library) actually sounds non-American.
// So treat them like ElevenLabs voices: tag each with its native accent and
// filter the picker by the user's selected variant.
//
// `fable` is the only British voice. There is no Australian voice — en-AU
// users see the full list via the empty-filter fallback in [filterByVariant].
// The other five (alloy / echo / onyx / nova / shimmer) sound American.
val OPENAI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("alloy", "alloy — Neutral", VoiceLocale.EN_US),
    TtsVoiceOption("echo", "echo — Soft, male", VoiceLocale.EN_US),
    TtsVoiceOption("fable", "fable — Storyteller", VoiceLocale.EN_GB),
    TtsVoiceOption("onyx", "onyx — Deep, male", VoiceLocale.EN_US),
    TtsVoiceOption("nova", "nova — Bright, female", VoiceLocale.EN_US),
    TtsVoiceOption("shimmer", "shimmer — Warm, female", VoiceLocale.EN_US),
)

// ElevenLabs voice IDs are opaque strings; the human-readable name only shows in
// the picker. These are the popular pre-made library voices. Users with a paid
// plan can clone their own voice — they'd need to copy/paste the voice ID by
// hand, which we don't surface in v1.
//
// Each entry is tagged with the voice's native accent — ElevenLabs voices are
// clones of specific speakers, so the accent is fixed and can't be steered by
// prompt. The Settings picker filters by the user's selected variant; if the
// filter empties out we fall back to the full list. The v1 library is entirely
// en-US, so en-GB and en-AU users see the fallback today.
val ELEVENLABS_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("EXAVITQu4vr4xnSDxMaL", "Sarah — Warm, female", VoiceLocale.EN_US),
    TtsVoiceOption("21m00Tcm4TlvDq8ikWAM", "Rachel — Calm, female", VoiceLocale.EN_US),
    TtsVoiceOption("MF3mGyEYCl7XYWbV9V6O", "Elli — Emotional, female", VoiceLocale.EN_US),
    TtsVoiceOption("pNInz6obpgDQGcFmaJgB", "Adam — Deep, male", VoiceLocale.EN_US),
    TtsVoiceOption("ErXwobaYiN019PkySvjV", "Antoni — Well-rounded, male", VoiceLocale.EN_US),
    TtsVoiceOption("TxGEqnHWrfWFTfGW9XjX", "Josh — Young, male", VoiceLocale.EN_US),
    TtsVoiceOption("VR6AewLTigWG4xSOukaG", "Arnold — Crisp, male", VoiceLocale.EN_US),
)

/**
 * Adapts a list of [ElevenLabsVoiceSummary] (the wire-side projection from
 * `GET /v1/voices`) into the picker's [TtsVoiceOption] type.
 *
 * Display-name shape mirrors the curated [ELEVENLABS_VOICES] format and
 * folds in whatever metadata the API returned, so users with similarly-
 * named voices (e.g. several clones called "Mike") can still tell them
 * apart in the picker:
 *
 *  - both description and accent: `"Sarah — warm (american)"`
 *  - description only:            `"Sarah — warm"`
 *  - accent only:                 `"Sarah (american)"`
 *  - neither (typical clone):     `"Sarah"`
 *
 * The voice's [TtsVoiceOption.locale] is best-effort mapped from the
 * ElevenLabs `accent` label (see [parseElevenLabsAccent]) so the picker's
 * locale filter narrows the refreshed list the same way it narrows the
 * curated one. Unrecognised or missing accent labels map to `null`,
 * which the filter treats as accent-agnostic — better to show a voice
 * than to hide it because we couldn't classify the accent string.
 */
fun List<ElevenLabsVoiceSummary>.toVoiceOptions(): List<TtsVoiceOption> = map { summary ->
    val description = summary.description?.takeIf { it.isNotBlank() }
    val accent = summary.accent?.takeIf { it.isNotBlank() }
    val displayName = buildString {
        append(summary.name)
        if (description != null) append(" — ").append(description)
        if (accent != null) append(" (").append(accent).append(')')
    }
    TtsVoiceOption(
        id = summary.id,
        displayName = displayName,
        locale = parseElevenLabsAccent(accent),
    )
}

/**
 * Maps ElevenLabs's free-form `accent` label onto a [VoiceLocale] when we
 * can do so unambiguously. Returns `null` for anything we don't recognise
 * — including ambiguous labels like "transatlantic" and labels that
 * straddle multiple `VoiceLocale` values ("irish", "scottish") — so the
 * locale filter doesn't drop those voices wrongly.
 *
 * The label is lowercased and trimmed before lookup; ElevenLabs uses
 * lowercase by convention but custom voices can use anything.
 *
 * Kept conservative on purpose: false positives are worse than false
 * negatives here. A US user filtering to en-US who sees an extra
 * "transatlantic" voice can ignore it; one whose only good clone is
 * silently filtered out can't recover without flipping the picker
 * back to "Follow language setting".
 */
private fun parseElevenLabsAccent(label: String?): VoiceLocale? {
    // Locale.ROOT avoids the Turkish-locale gotcha where lowercasing "I"
    // yields "ı" rather than "i", which would silently break the lookup
    // for any user whose phone is set to tr-TR.
    val key = label?.trim()?.lowercase(java.util.Locale.ROOT) ?: return null
    return ACCENT_TO_VOICE_LOCALE[key]
}

private val ACCENT_TO_VOICE_LOCALE: Map<String, VoiceLocale> = mapOf(
    // English variants — the ones ElevenLabs ships in the premade library.
    "american" to VoiceLocale.EN_US,
    "us english" to VoiceLocale.EN_US,
    "british" to VoiceLocale.EN_GB,
    "english" to VoiceLocale.EN_GB,
    "british english" to VoiceLocale.EN_GB,
    "australian" to VoiceLocale.EN_AU,
    "canadian" to VoiceLocale.EN_CA,
    "south african" to VoiceLocale.EN_ZA,
    // Other locales we expose in the picker. Map the language name to the
    // single VoiceLocale we have for it; users on the regional variants
    // (es-MX, ar-EG, …) rely on the empty-filter fallback to still see
    // these voices.
    "german" to VoiceLocale.DE_DE,
    "french" to VoiceLocale.FR_FR,
    "italian" to VoiceLocale.IT_IT,
    "spanish" to VoiceLocale.ES_ES,
    "catalan" to VoiceLocale.CA_ES,
    "portuguese" to VoiceLocale.PT_BR,
    "brazilian" to VoiceLocale.PT_BR,
    "russian" to VoiceLocale.RU_RU,
    "polish" to VoiceLocale.PL_PL,
    "croatian" to VoiceLocale.HR_HR,
    "slovenian" to VoiceLocale.SL_SI,
    "greek" to VoiceLocale.EL_GR,
    "ukrainian" to VoiceLocale.UK_UA,
    "dutch" to VoiceLocale.NL_NL,
    "swedish" to VoiceLocale.SV_SE,
    "danish" to VoiceLocale.DA_DK,
    "norwegian" to VoiceLocale.NB_NO,
    "finnish" to VoiceLocale.FI_FI,
    "estonian" to VoiceLocale.ET_EE,
    "latvian" to VoiceLocale.LV_LV,
    "lithuanian" to VoiceLocale.LT_LT,
    "turkish" to VoiceLocale.TR_TR,
    "indonesian" to VoiceLocale.ID_ID,
    "malay" to VoiceLocale.MS_MY,
    "filipino" to VoiceLocale.FIL_PH,
    "vietnamese" to VoiceLocale.VI_VN,
    "thai" to VoiceLocale.TH_TH,
    "chinese" to VoiceLocale.ZH_CN,
    "hindi" to VoiceLocale.HI_IN,
    "bengali" to VoiceLocale.BN_BD,
    "japanese" to VoiceLocale.JA_JP,
    "korean" to VoiceLocale.KO_KR,
    "arabic" to VoiceLocale.AR_SA,
    "hebrew" to VoiceLocale.HE_IL,
    "persian" to VoiceLocale.FA_IR,
    "amharic" to VoiceLocale.AM_ET,
    "swahili" to VoiceLocale.SW_KE,
    "kiswahili" to VoiceLocale.SW_KE,
)
