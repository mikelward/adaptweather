package app.clothescast.tts

import app.clothescast.core.data.tts.DEFAULT_GEMINI_TTS_VOICE
import app.clothescast.core.data.tts.GeminiTtsClient
import java.util.Locale

/**
 * High-quality TTS via Gemini's audio-output model. Synthesises PCM audio in one
 * network call, hands the buffer to [PcmAudioPlayer] for playback.
 *
 * Voice is configurable; defaults to `Kore` (firm). Other prebuilt voices are
 * surfaced via the Settings voice picker.
 *
 * Fallback is the caller's responsibility — see [app.clothescast.work.FetchAndNotifyWorker]
 * which retries with [AndroidTtsSpeaker] when this throws.
 */
class GeminiTtsSpeaker(
    private val client: GeminiTtsClient,
    private val voiceName: String = DEFAULT_GEMINI_TTS_VOICE,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val audio = client.synthesize(text = text, voiceName = voiceName)
        PcmAudioPlayer.play(audio)
    }
}
