package app.adaptweather.tts

import app.adaptweather.core.data.tts.DEFAULT_ELEVENLABS_TTS_VOICE
import app.adaptweather.core.data.tts.ElevenLabsTtsClient
import java.util.Locale

/**
 * TTS via ElevenLabs's `text-to-speech` endpoint. Synthesises PCM audio in one
 * network call, hands the buffer to [PcmAudioPlayer] for playback.
 *
 * Voice is an ElevenLabs voice ID (the long opaque identifier from their voice
 * library, e.g. `EXAVITQu4vr4xnSDxMaL` for Sarah); selectable from Settings.
 *
 * Fallback is the caller's responsibility — see [app.adaptweather.work.FetchAndNotifyWorker]
 * which retries with [AndroidTtsSpeaker] when this throws.
 */
class ElevenLabsTtsSpeaker(
    private val client: ElevenLabsTtsClient,
    private val voiceId: String = DEFAULT_ELEVENLABS_TTS_VOICE,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val audio = client.synthesize(text = text, voiceId = voiceId)
        PcmAudioPlayer.play(audio)
    }
}
