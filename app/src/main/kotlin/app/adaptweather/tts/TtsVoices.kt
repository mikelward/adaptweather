package app.adaptweather.tts

/**
 * Curated voice lists for the user-facing voice picker. Each provider has many more
 * options than this — these are the ones surfaced in Settings to keep the picker
 * scannable.
 *
 * The [id] is what's persisted and sent to the API. The [displayName] is shown in the
 * dropdown and is intentionally English-only for v1; if/when we localize, these move
 * to strings.xml.
 */
data class TtsVoiceOption(val id: String, val displayName: String)

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
