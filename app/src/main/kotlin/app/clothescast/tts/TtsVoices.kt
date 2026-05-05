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

// Curated from listening tests across en-AU and en-GB. Leda is the default —
// chosen alongside the v505-equivalent "Normal" style preamble being the
// default style; pending a broader re-audition under Normal. Grouped by
// character:
//   Clear/Firm (newsreader-y): Erinome, Iapetus, Kore
//   Informative/Knowledgeable (male alternatives): Charon, Sadaltager
//   Gentle/Smooth/Warm: Vindemiatrix, Despina, Algieba, Sulafat
//   Breezy/Youthful: Aoede, Leda
// Aoede and Leda were dropped in the previous curation under the newsreader
// directive (Aoede sounded "Alan Rickman" on en-GB+newsreader; Leda flattened
// in newsreader register). Both come back now that the user can pick the
// "Normal" style preamble — Leda is the originating ask. Sulafat / Algieba
// re-added as additional warm/smooth options. Still dropped: Orus
// (theatrical), Achird (vowel issues), Zephyr / Puck / Fenrir (theatrical /
// excitable) — those are voice-character traits not fixed by switching style.
val GEMINI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("Erinome", "Erinome — Clear"),
    TtsVoiceOption("Iapetus", "Iapetus — Clear"),
    TtsVoiceOption("Kore", "Kore — Firm"),
    TtsVoiceOption("Charon", "Charon — Informative"),
    TtsVoiceOption("Sadaltager", "Sadaltager — Knowledgeable"),
    TtsVoiceOption("Vindemiatrix", "Vindemiatrix — Gentle"),
    TtsVoiceOption("Despina", "Despina — Smooth"),
    TtsVoiceOption("Algieba", "Algieba — Smooth"),
    TtsVoiceOption("Sulafat", "Sulafat — Warm"),
    TtsVoiceOption("Aoede", "Aoede — Breezy"),
    TtsVoiceOption("Leda", "Leda — Youthful"),
)
