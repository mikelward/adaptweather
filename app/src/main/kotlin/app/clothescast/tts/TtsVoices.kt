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

// Curated from listening tests across en-GB, en-AU, en-US, and de-* under the
// post-#353 shipping configuration (clarity-trimmed accents, B3 register-in-
// directive routing on en-AU). Despina is the default — validated across all
// six tested locales, four-roll en-AU B3 distribution all ≥8 (the user's
// acceptance bar). Order: Despina (default) first, then by character cluster.
//
// Leda showed wide en-AU B3 4-roll variance (5 / 7.5 / 8 / 7) in eval but
// stays in the picker on user preference — qualitatively strong on real-world
// audio. She loses the default slot to Despina's tighter distribution but is
// retained for users who want her character.
//
// Dropped from the previous 11-voice picker: Sadaltager, Vindemiatrix,
// Algieba, Sulafat (untested under current shipping config — trimming the
// picker to voices the eval has actually validated). Earlier drops stand:
// Orus (theatrical), Achird (vowel issues), Zephyr / Puck / Fenrir
// (theatrical / excitable). See docs/voice-evals.md for the data.
val GEMINI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("Despina", "Despina — Smooth"),
    TtsVoiceOption("Charon", "Charon — Informative"),
    TtsVoiceOption("Iapetus", "Iapetus — Clear"),
    TtsVoiceOption("Kore", "Kore — Firm"),
    TtsVoiceOption("Erinome", "Erinome — Clear"),
    TtsVoiceOption("Aoede", "Aoede — Breezy"),
    TtsVoiceOption("Leda", "Leda — Youthful"),
)
