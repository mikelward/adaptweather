package app.clothescast.tts

import app.clothescast.core.domain.model.VoiceLocale

/**
 * Curated voice list for the user-facing voice picker. Gemini ships many more
 * voices than this — these are the ones surfaced in Settings to keep the picker
 * scannable.
 *
 * The [id] is what's persisted and sent to the API. The [displayName] is shown in the
 * dropdown and is intentionally English-only for v1; if/when we localize, these move
 * to strings.xml.
 *
 * [locale] would be the voice's *baked-in* accent for engines whose voices have a
 * fixed accent that can't be reliably steered by prompt. Today every voice we ship
 * is from Gemini, whose prebuilt voices are language-agnostic personalities that
 * reliably follow accent direction in the prompt — so [locale] is null for all of
 * them and the picker does no filtering. The field is kept around so a future
 * accent-locked engine can plug in without reshaping this type.
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
