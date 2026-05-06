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

val GEMINI_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("Iapetus", "Iapetus — Clear"),
    TtsVoiceOption("Charon", "Charon — Informative"),
    TtsVoiceOption("Despina", "Despina — Smooth"),
    TtsVoiceOption("Leda", "Leda — Youthful"),
)
