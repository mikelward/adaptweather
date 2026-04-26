package app.clothescast.tts

import app.clothescast.core.data.tts.DEFAULT_OPENAI_TTS_VOICE
import app.clothescast.core.data.tts.OpenAITtsClient
import java.util.Locale

/**
 * TTS via OpenAI's `audio/speech` endpoint. Synthesises PCM audio in one network
 * call, hands the buffer to [PcmAudioPlayer] for playback.
 *
 * Voice is one of the OpenAI stock voices (alloy, echo, fable, onyx, nova,
 * shimmer); selectable from Settings.
 *
 * Fallback is the caller's responsibility — see [app.clothescast.work.FetchAndNotifyWorker]
 * which retries with [AndroidTtsSpeaker] when this throws.
 */
class OpenAITtsSpeaker(
    private val client: OpenAITtsClient,
    private val voice: String = DEFAULT_OPENAI_TTS_VOICE,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val audio = client.synthesize(text = text, voice = voice)
        PcmAudioPlayer.play(audio)
    }
}
