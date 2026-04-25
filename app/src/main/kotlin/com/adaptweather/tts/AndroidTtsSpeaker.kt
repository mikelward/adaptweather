package com.adaptweather.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [TtsSpeaker] that wraps Android's [TextToSpeech] engine.
 *
 * Each call creates a fresh engine connection, speaks one utterance, and shuts down.
 * That keeps memory low and avoids cross-call state — the Worker fires once a day, so
 * the per-call init cost (~50–200ms) is negligible.
 */
class AndroidTtsSpeaker(private val context: Context) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val tts = initEngine()
        try {
            tts.setLanguage(locale)
            speakAndAwait(tts, text)
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun initEngine(): TextToSpeech = suspendCancellableCoroutine { cont ->
        var engineRef: TextToSpeech? = null
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                cont.resume(engineRef!!)
            } else {
                cont.resumeWithException(
                    IllegalStateException("TTS engine init failed (status=$status)"),
                )
            }
        }
        engineRef = engine
        cont.invokeOnCancellation { runCatching { engine.shutdown() } }
    }

    private suspend fun speakAndAwait(tts: TextToSpeech, text: String) {
        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Required override; modern TTS engines call onError(id, errorCode) instead.")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resumeWithException(IllegalStateException("TTS synthesis failed"))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("TTS synthesis failed (errorCode=$errorCode)"),
                        )
                    }
                }
            })

            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                cont.resumeWithException(IllegalStateException("TTS speak() rejected the utterance"))
            }
            cont.invokeOnCancellation { runCatching { tts.stop() } }
        }
        Log.i(TAG, "Spoke utterance ${utteranceId.take(8)}…")
    }

    companion object {
        private const val TAG = "AndroidTtsSpeaker"
    }
}
