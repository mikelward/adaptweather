package app.clothescast.tts

import java.util.Locale

/**
 * Speaks text aloud through the platform TTS engine. Suspending so the Worker can
 * await completion before declaring the run successful.
 *
 * Implementations should:
 * - Manage the lifecycle of the underlying engine (init on first use, shutdown after).
 * - Set the language to the requested [Locale] when supported, falling back to the
 *   engine default when not.
 * - Throw on init / synthesis failure so the caller can route to Result.retry.
 * - Run incoming text through [prepareForTts] before handing it to the engine, so
 *   the brand "ClothesCast" is pronounced as two clear syllables instead of being
 *   collapsed to "ClotheCast".
 */
interface TtsSpeaker {
    suspend fun speak(text: String, locale: Locale = Locale.getDefault())
}

/**
 * Rewrites the brand name "ClothesCast" → "Clothes Cast" before handing text to a
 * TTS engine. Every engine we ship — device TTS, Gemini, OpenAI, ElevenLabs —
 * tends to elide the `s` at the `s‑c` cluster boundary, producing "ClotheCast".
 * Splitting on a literal space is the only trick that works across all four
 * (SSML / phoneme markup support is uneven and the OpenAI / ElevenLabs REST
 * endpoints don't accept it). The notification body keeps the unbroken
 * spelling — only the spoken path goes through this transform.
 */
internal fun prepareForTts(text: String): String =
    text.replace("ClothesCast", "Clothes Cast")
