package app.clothescast.tts

/**
 * Curated voice list for the user-facing voice picker. Only Gemini's prebuilt
 * voices need a curated list — the on-device engine enumerates voices via
 * Android's TextToSpeech API.
 *
 * The [id] is what's persisted and sent to the API. The [displayName] is shown
 * in the dropdown and is intentionally English-only for v1; if/when we
 * localize, these move to strings.xml.
 */
data class TtsVoiceOption(
    val id: String,
    val displayName: String,
)

// Curated from listening tests across en-AU and en-GB. Erinome is the default
// — excellent across both locales. Clear/Firm (Erinome, Iapetus, Kore) for a
// crisp newsreader delivery; Gentle/Smooth (Vindemiatrix, Despina) for softer;
// Informative/Knowledgeable (Charon, Sadaltager) as male alternatives.
// Dropped: Orus (theatrical), Aoede (Alan Rickman with newsreader+en-GB),
// Achird, and the remaining 9 that were theatrical or had vowel issues.
val GEMINI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("Erinome", "Erinome — Clear"),
    TtsVoiceOption("Iapetus", "Iapetus — Clear"),
    TtsVoiceOption("Kore", "Kore — Firm"),
    TtsVoiceOption("Charon", "Charon — Informative"),
    TtsVoiceOption("Sadaltager", "Sadaltager — Knowledgeable"),
    TtsVoiceOption("Vindemiatrix", "Vindemiatrix — Gentle"),
    TtsVoiceOption("Despina", "Despina — Smooth"),
)
