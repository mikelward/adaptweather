package com.adaptweather.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.adaptweather.core.data.tts.GeminiTtsClient
import com.adaptweather.core.data.tts.PcmAudio
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * High-quality TTS via Gemini's audio-output model. Synthesises PCM audio in one
 * network call and plays it back through [AudioTrack] in MODE_STATIC, which lets
 * us hand the entire buffer at once and listen for the playback-head reaching the
 * end. Cleaner than streaming from a short utterance.
 *
 * Fallback is the caller's responsibility — see [com.adaptweather.work.FetchAndNotifyWorker]
 * which retries with [AndroidTtsSpeaker] when this throws.
 */
class GeminiTtsSpeaker(
    private val client: GeminiTtsClient,
    private val voiceName: String? = null,
) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val audio = client.synthesize(
            text = text,
            voiceName = voiceName ?: com.adaptweather.core.data.tts.DEFAULT_GEMINI_TTS_VOICE,
        )
        play(audio)
    }

    private suspend fun play(audio: PcmAudio) {
        val pcm = audio.bytes
        if (pcm.isEmpty()) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            // MODE_STATIC accepts the whole buffer in one write before play().
            val written = track.write(pcm, 0, pcm.size)
            if (written < 0) {
                Log.w(TAG, "AudioTrack.write rejected the buffer (code=$written)")
                return
            }
            // 2 bytes per 16-bit sample, mono.
            val totalFrames = pcm.size / 2
            track.notificationMarkerPosition = totalFrames
            awaitMarker(track, totalFrames)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private suspend fun awaitMarker(track: AudioTrack, markerInFrames: Int) {
        suspendCancellableCoroutine<Unit> { cont ->
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onPeriodicNotification(t: AudioTrack?) {}
                },
            )
            track.play()
            cont.invokeOnCancellation { runCatching { track.stop() } }
        }
        Log.i(TAG, "Played ${markerInFrames} PCM frames")
    }

    companion object {
        private const val TAG = "GeminiTtsSpeaker"
    }
}
