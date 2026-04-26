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
    TtsVoiceOption("Puck", "Puck — Upbeat"),
    TtsVoiceOption("Zephyr", "Zephyr — Bright"),
    TtsVoiceOption("Leda", "Leda — Youthful"),
    TtsVoiceOption("Aoede", "Aoede — Breezy"),
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
val ELEVENLABS_VOICES: List<TtsVoiceOption> = listOf(
    TtsVoiceOption("EXAVITQu4vr4xnSDxMaL", "Sarah — Warm, female"),
    TtsVoiceOption("21m00Tcm4TlvDq8ikWAM", "Rachel — Calm, female"),
    TtsVoiceOption("AZnzlk1v9MwlGOIKvxn0", "Domi — Strong, female"),
    TtsVoiceOption("MF3mGyEYCl7XYWbV9V6O", "Elli — Emotional, female"),
    TtsVoiceOption("pNInz6obpgDQGcFmaJgB", "Adam — Deep, male"),
    TtsVoiceOption("ErXwobaYiN019PkySvjV", "Antoni — Well-rounded, male"),
    TtsVoiceOption("TxGEqnHWrfWFTfGW9XjX", "Josh — Young, male"),
    TtsVoiceOption("VR6AewLTigWG4xSOukaG", "Arnold — Crisp, male"),
)
