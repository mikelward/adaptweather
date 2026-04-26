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
 */
interface TtsSpeaker {
    suspend fun speak(text: String, locale: Locale = Locale.getDefault())
}
