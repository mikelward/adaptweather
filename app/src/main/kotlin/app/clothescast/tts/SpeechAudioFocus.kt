package app.clothescast.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import app.clothescast.diag.DiagLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds audio focus around [block] so the spoken briefing doesn't talk over
 * music or a podcast.
 *
 * Two-stage strategy:
 * 1. Try `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` first — polite. Other apps just
 *    drop their volume for the duration; nothing pauses. This is the path
 *    taken in the common case (no phone call, no exclusive holder).
 * 2. If ducking is refused (typically because a phone call holds exclusive
 *    focus), retry with `AUDIOFOCUS_GAIN` + `setAcceptsDelayedFocusGain(true)`.
 *    The system queues us until the call ends, then fires `AUDIOFOCUS_GAIN`
 *    on the listener — at which point we proceed. Only `AUDIOFOCUS_GAIN`
 *    supports delayed grant; ducking grants are immediate-or-fail.
 *
 * The delayed wait is bounded ([MAX_DELAYED_FOCUS_WAIT_MS]) so a long phone
 * call doesn't hold a [androidx.work.CoroutineWorker] open until WorkManager
 * kills the whole job. Past the cap, the briefing is stale enough that the
 * notification (which fired before TTS) is the better delivery anyway, so we
 * speak at full volume rather than silently swallowing the briefing.
 */
internal suspend fun <T> withSpeechAudioFocus(
    context: Context,
    block: suspend () -> T,
): T {
    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return block()

    // The listener Handler must be supplied explicitly: the no-arg
    // setOnAudioFocusChangeListener overload uses the calling thread's Looper,
    // and we're invoked from Dispatchers.IO / WorkManager — neither has one,
    // so Builder.build() would crash with "Can't create handler inside thread
    // that has not called Looper.prepare()". Pin the listener to the main
    // Looper, which is always available.
    val mainHandler = Handler(Looper.getMainLooper())
    val ducking = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(SPEECH_ATTRS)
        .setOnAudioFocusChangeListener({}, mainHandler)
        .build()
    if (am.requestAudioFocus(ducking) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        return try {
            block()
        } finally {
            runCatching { am.abandonAudioFocusRequest(ducking) }
        }
    }

    // Ducking refused — most often a phone call holding exclusive focus. Retry
    // with an exclusive request that the system can defer until the holder
    // releases. Past the cap we give up and speak anyway.
    val granted = CompletableDeferred<Boolean>()
    val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN ->
                if (!granted.isCompleted) granted.complete(true)
            AudioManager.AUDIOFOCUS_LOSS ->
                if (!granted.isCompleted) granted.complete(false)
        }
    }
    val exclusive = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(SPEECH_ATTRS)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(listener, mainHandler)
        .build()
    when (am.requestAudioFocus(exclusive)) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> Unit
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
            DiagLog.i(TAG, "Audio focus delayed (likely a call); waiting up to ${MAX_DELAYED_FOCUS_WAIT_MS}ms")
            val gotIt = withTimeoutOrNull(MAX_DELAYED_FOCUS_WAIT_MS) { granted.await() } == true
            if (!gotIt) DiagLog.w(TAG, "Gave up waiting for delayed audio focus; speaking anyway.")
        }
        else -> DiagLog.w(TAG, "Audio focus refused outright; speaking anyway.")
    }
    return try {
        block()
    } finally {
        runCatching { am.abandonAudioFocusRequest(exclusive) }
    }
}

// 5 minutes. Catches typical short calls (under ~3 min) with margin while
// staying well inside WorkManager's ~10-minute soft job ceiling and well
// short of the point where the briefing is too stale to matter.
private const val MAX_DELAYED_FOCUS_WAIT_MS: Long = 5 * 60 * 1000L

private val SPEECH_ATTRS: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

private const val TAG = "SpeechAudioFocus"
