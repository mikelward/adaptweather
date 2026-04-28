package app.clothescast.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.clothescast.diag.DiagLog

/**
 * Holds transient audio focus with ducking around [block] so the spoken briefing
 * doesn't talk over music or a podcast — other apps drop their volume for the
 * duration and recover when we abandon focus. [block] always runs: if focus is
 * denied (rare; usually a phone call), we log and play at full volume rather
 * than swallow the briefing the user is waiting on.
 *
 * Used at the *playback session* boundary (the worker's speak-with-fallback,
 * the Settings preview) rather than per-engine, so the focus is held across the
 * whole "try cloud, fall back to device" chain in one request.
 */
internal suspend fun <T> withSpeechAudioFocus(
    context: Context,
    block: suspend () -> T,
): T {
    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return block()
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener {}
        .build()
    val granted = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    if (!granted) DiagLog.w(TAG, "Audio focus not granted; speaking anyway.")
    return try {
        block()
    } finally {
        runCatching { am.abandonAudioFocusRequest(request) }
    }
}

private const val TAG = "SpeechAudioFocus"
