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
 * Filters [voices] to the user's preferred [variant]. Falls back to the full
 * list when the filter would empty out — handing the user an empty picker is
 * worse than offering whatever voices we have, even if the accent doesn't match.
 *
 * Voices with a null [TtsVoiceOption.locale] are accent-agnostic and always
 * pass the filter. [VoiceLocale.SYSTEM] disables filtering — the user has not
 * expressed a preference, so show everything.
 *
 * If [keepSelected] is supplied and points to a voice in the receiver list
 * that doesn't match the filter, that voice is appended so the picker still
 * shows the user's current selection. Without this, a user who'd persisted
 * `nova` and then switched the variant to en-GB would see a picker label of
 * "Voice: nova" with no `nova` row in the dialog — confusing UX. Pass `null`
 * (the default) when this preservation isn't needed.
 */
fun List<TtsVoiceOption>.filterByVariant(
    variant: VoiceLocale,
    keepSelected: String? = null,
): List<TtsVoiceOption> {
    if (variant == VoiceLocale.SYSTEM) return this
    val matched = filter { it.locale == null || it.locale == variant }
    if (matched.isEmpty()) return this
    val selected = keepSelected?.let { id -> firstOrNull { it.id == id } }
    return if (selected != null && selected !in matched) matched + selected else matched
}

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
 * We deliberately leave [TtsVoiceOption.locale] null on every refreshed
 * entry rather than trying to map ElevenLabs's free-form `accent` label
 * ("american", "british", "australian", "transatlantic", custom user
 * strings, …) to [VoiceLocale]: the user explicitly hit "Refresh", so they
 * want to see what their key has access to without the locale filter
 * dropping non-matching accents. Cloned voices in particular often have no
 * accent label at all.
 */
fun List<ElevenLabsVoiceSummary>.toVoiceOptions(): List<TtsVoiceOption> = map { summary ->
    val description = summary.description?.takeIf { it.isNotBlank() }
    val accent = summary.accent?.takeIf { it.isNotBlank() }
    val displayName = buildString {
        append(summary.name)
        if (description != null) append(" — ").append(description)
        if (accent != null) append(" (").append(accent).append(')')
    }
    TtsVoiceOption(id = summary.id, displayName = displayName, locale = null)
}
