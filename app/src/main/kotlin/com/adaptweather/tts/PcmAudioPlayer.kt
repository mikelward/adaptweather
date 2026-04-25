package com.adaptweather.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.adaptweather.core.data.tts.PcmAudio
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Plays a chunk of 16-bit signed mono PCM through [AudioTrack] in `MODE_STATIC`,
 * suspending until the playback head reaches the end of the buffer.
 *
 * Shared by [GeminiTtsSpeaker] and [OpenAITtsSpeaker] — both providers return PCM
 * with the same encoding, only the sample rate differs (Gemini reports it in the
 * response mimeType; OpenAI fixes it at 24 kHz).
 */
internal object PcmAudioPlayer {

    private const val TAG = "PcmAudioPlayer"

    suspend fun play(audio: PcmAudio) {
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
            // MODE_STATIC normally accepts the whole buffer in a single write because
            // we sized the track to pcm.size — but the contract permits a positive
            // short write, in which case we'd set the marker beyond the actual frame
            // count and awaitMarker would never resume. Loop until the buffer is
            // fully accepted; bail on any error code or zero-progress write.
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) {
                    Log.w(TAG, "AudioTrack.write returned $written at offset $offset; aborting playback")
                    return
                }
                offset += written
            }
            // 2 bytes per 16-bit sample, mono. Validate alignment defensively — a
            // partial-write loop ending on an odd byte boundary would point the
            // marker at a non-existent frame.
            if (offset % 2 != 0) {
                Log.w(TAG, "AudioTrack accepted an odd number of bytes ($offset); aborting playback")
                return
            }
            val totalFrames = offset / 2
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
        Log.i(TAG, "Played $markerInFrames PCM frames")
    }
}
