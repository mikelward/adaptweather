package app.clothescast.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [TtsSpeaker] that wraps Android's [TextToSpeech] engine.
 *
 * Each call creates a fresh engine connection, picks the best available voice for the
 * requested locale, speaks one utterance, and shuts down. The Worker fires once a day,
 * so per-call init cost (~50–200ms) is negligible.
 *
 * Voice quality:
 * 1. We force-select Google's "Speech Services by Google" engine ([GOOGLE_TTS_PACKAGE])
 *    when it's installed, since most vendor-default TTS engines on Android sound
 *    significantly worse. If Google's engine isn't installed we fall back to the
 *    system default.
 * 2. From the chosen engine's voice list, we pick the highest-quality voice that
 *    matches the requested [Locale] — preferring exact-locale matches, then same-
 *    language matches. Voice quality runs from VERY_LOW (100) through VERY_HIGH (500).
 *
 * TODO: evaluate higher-quality alternatives. Two candidates:
 *   - Gemini's audio-output model (`gemini-2.5-flash-preview-tts`). Uses the existing
 *     BYOK key. Network round-trip per speak; produces near-human voices.
 *   - ElevenLabs / OpenAI TTS. Highest fidelity but adds another vendor + key.
 */
class AndroidTtsSpeaker(private val context: Context) : TtsSpeaker {

    override suspend fun speak(text: String, locale: Locale) {
        val tts = initEngine()
        try {
            applyBestVoice(tts, locale)
            speakAndAwait(tts, prepareForTts(text))
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun initEngine(): TextToSpeech {
        // Try Google's engine first; if it isn't installed (or fails to bind), Android
        // returns ERROR from the init callback and we fall back to the system default.
        return runCatching { initEngine(GOOGLE_TTS_PACKAGE) }
            .getOrElse {
                Log.w(TAG, "Google TTS unavailable; falling back to system default.", it)
                initEngine(null)
            }
    }

    private suspend fun initEngine(enginePackage: String?): TextToSpeech =
        suspendCancellableCoroutine { cont ->
            var engineRef: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    cont.resume(engineRef!!)
                } else {
                    cont.resumeWithException(
                        IllegalStateException("TTS engine init failed (status=$status)"),
                    )
                }
            }
            val engine = if (enginePackage != null) {
                TextToSpeech(context, listener, enginePackage)
            } else {
                TextToSpeech(context, listener)
            }
            engineRef = engine
            cont.invokeOnCancellation { runCatching { engine.shutdown() } }
        }

    private fun applyBestVoice(tts: TextToSpeech, locale: Locale) {
        tts.setLanguage(locale)
        val voices: Set<Voice> = runCatching { tts.voices }.getOrNull() ?: emptySet()
        if (voices.isEmpty()) return

        val exactMatch = voices.filter { it.locale == locale }
        val sameLanguage = voices.filter { it.locale.language == locale.language }
        val candidates = when {
            exactMatch.isNotEmpty() -> exactMatch
            sameLanguage.isNotEmpty() -> sameLanguage
            else -> voices.toList()
        }

        // Quality runs VERY_LOW (100) → VERY_HIGH (500). Prefer non-network voices on
        // ties so a flaky connection at speak-time doesn't break playback.
        val best = candidates.maxWithOrNull(
            compareBy<Voice> { it.quality }.thenBy { !it.isNetworkConnectionRequired },
        )
        if (best != null) {
            runCatching { tts.voice = best }
                .onSuccess {
                    Log.i(TAG, "TTS voice: ${best.name} (quality=${best.quality}, locale=${best.locale})")
                }
                .onFailure { Log.w(TAG, "Setting TTS voice failed; using engine default.", it) }
        }
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
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }
}
