package app.clothescast.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.clothescast.diag.DiagLog
import app.clothescast.core.data.tts.PcmAudio
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Plays a chunk of 16-bit signed mono PCM through [AudioTrack] in `MODE_STREAM`,
 * suspending until the playback head reaches the end of the buffer.
 *
 * Shared by [GeminiTtsSpeaker] and [OpenAITtsSpeaker] — both providers return PCM
 * with the same encoding, only the sample rate differs (Gemini reports it in the
 * response mimeType; OpenAI fixes it at 24 kHz).
 *
 * Uses `USAGE_ASSISTANT` to bypass the notification stream's compression/limiting,
 * which audibly distorts speech at 24 kHz. `MODE_STREAM` (rather than `MODE_STATIC`)
 * avoids end-of-buffer pops observed on some devices.
 */
internal object PcmAudioPlayer {

    private const val TAG = "PcmAudioPlayer"

    suspend fun play(audio: PcmAudio) {
        val pcm = audio.bytes
        if (pcm.isEmpty()) return
        // 16-bit mono → 2 bytes per frame. An odd payload means the response was
        // truncated mid-sample; setting a marker past the last whole frame would
        // either click or never fire.
        if (pcm.size % 2 != 0) {
            DiagLog.w(TAG, "PCM payload has odd byte count (${pcm.size}); aborting playback")
            return
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            DiagLog.w(TAG, "getMinBufferSize returned $minBuffer for ${audio.sampleRate}Hz; aborting playback")
            return
        }
        // A few periods of headroom smooth over jitter on slower devices without
        // adding meaningful latency for short utterances.
        val bufferBytes = minBuffer * 2

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            val totalFrames = pcm.size / 2
            track.notificationMarkerPosition = totalFrames
            track.play()

            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) {
                    DiagLog.w(TAG, "AudioTrack.write returned $written at offset $offset; aborting playback")
                    return
                }
                offset += written
            }
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
            cont.invokeOnCancellation { runCatching { track.stop() } }
        }
        DiagLog.i(TAG, "Played $markerInFrames PCM frames")
    }
}
