package app.clothescast.tts

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
 * voices have a fixed accent that can't be steered by prompt (ElevenLabs voice
 * clones). Null means the picker does not filter that voice by accent: both
 * Gemini's prebuilt voices and OpenAI's `gpt-4o-mini-tts` voices are
 * language-agnostic and accept accent steering via prompt directives at
 * synthesis time. The picker only filters by [locale] when it's non-null.
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
 */
fun List<TtsVoiceOption>.filterByVariant(variant: VoiceLocale): List<TtsVoiceOption> {
    if (variant == VoiceLocale.SYSTEM) return this
    val matched = filter { it.locale == null || it.locale == variant }
    return matched.ifEmpty { this }
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

val OPENAI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("alloy", "alloy — Neutral"),
    TtsVoiceOption("echo", "echo — Soft, male"),
    TtsVoiceOption("fable", "fable — Storyteller"),
    TtsVoiceOption("onyx", "onyx — Deep, male"),
    TtsVoiceOption("nova", "nova — Bright, female"),
    TtsVoiceOption("shimmer", "shimmer — Warm, female"),
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
    TtsVoiceOption("AZnzlk1v9MwlGOIKvxn0", "Domi — Strong, female", VoiceLocale.EN_US),
    TtsVoiceOption("MF3mGyEYCl7XYWbV9V6O", "Elli — Emotional, female", VoiceLocale.EN_US),
    TtsVoiceOption("pNInz6obpgDQGcFmaJgB", "Adam — Deep, male", VoiceLocale.EN_US),
    TtsVoiceOption("ErXwobaYiN019PkySvjV", "Antoni — Well-rounded, male", VoiceLocale.EN_US),
    TtsVoiceOption("TxGEqnHWrfWFTfGW9XjX", "Josh — Young, male", VoiceLocale.EN_US),
    TtsVoiceOption("VR6AewLTigWG4xSOukaG", "Arnold — Crisp, male", VoiceLocale.EN_US),
)
