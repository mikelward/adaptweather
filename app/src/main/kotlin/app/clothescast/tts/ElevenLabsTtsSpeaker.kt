package app.clothescast.tts

import app.clothescast.core.data.tts.DEFAULT_ELEVENLABS_TTS_MODEL
import app.clothescast.core.data.tts.DEFAULT_ELEVENLABS_TTS_SPEED
import app.clothescast.core.data.tts.DEFAULT_ELEVENLABS_TTS_VOICE
import app.clothescast.core.data.tts.ElevenLabsTtsClient
import java.util.Locale

/**
 * TTS via ElevenLabs's `text-to-speech` endpoint. Synthesises PCM audio in one
 * network call, hands the buffer to [PcmAudioPlayer] for playback.
 *
 * Voice is an ElevenLabs voice ID (the long opaque identifier from their voice
 * library, e.g. `EXAVITQu4vr4xnSDxMaL` for Sarah); selectable from Settings.
 * Model and speed are also user-selectable in Settings — defaults match the
 * client-level defaults so existing callers that don't pass them through
 * keep their previous behaviour.
 *
 * Fallback is the caller's responsibility — see [app.clothescast.work.FetchAndNotifyWorker]
 * which retries with [AndroidTtsSpeaker] when this throws.
 */
class ElevenLabsTtsSpeaker(
    private val client: ElevenLabsTtsClient,
    private val voiceId: String = DEFAULT_ELEVENLABS_TTS_VOICE,
    private val model: String = DEFAULT_ELEVENLABS_TTS_MODEL,
    private val speed: Double = DEFAULT_ELEVENLABS_TTS_SPEED,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val audio = client.synthesize(
            text = prepareForTts(text),
            voiceId = voiceId,
            model = model,
            speed = speed,
        )
        PcmAudioPlayer.play(audio)
    }
}
