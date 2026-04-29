package app.clothescast.tts

import app.clothescast.core.data.tts.DEFAULT_OPENAI_TTS_SPEED
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
    private val speed: Double = DEFAULT_OPENAI_TTS_SPEED,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        // [locale] is part of the [TtsSpeaker] contract (the device speaker uses
        // it to pick a system voice) but OpenAI's accent is purely voice-bound:
        // the right accent comes from the user picking the right voice in
        // Settings, not from anything we can send at synthesis time. So we drop
        // [locale] on the floor here, intentionally — see TtsVoices.kt for the
        // picker-side filter that surfaces the accent-appropriate voices.
        val audio = client.synthesize(text = prepareForTts(text), voice = voice, speed = speed)
        PcmAudioPlayer.play(audio)
    }
}
