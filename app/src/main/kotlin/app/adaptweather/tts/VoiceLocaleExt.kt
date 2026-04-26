package app.adaptweather.tts

import app.adaptweather.core.domain.model.VoiceLocale
import java.util.Locale

/**
 * Maps a [VoiceLocale] preference to a concrete [Locale].
 *
 * Returns null for [VoiceLocale.SYSTEM] so callers can fall back to
 * `Locale.getDefault()` themselves — keeps the "follow the phone" semantics
 * deferred to the call site instead of baking it in here.
 */
fun VoiceLocale.toJavaLocale(): Locale? = bcp47?.let { Locale.forLanguageTag(it) }

fun VoiceLocale.resolve(): Locale = toJavaLocale() ?: Locale.getDefault()

/**
 * Picks the most natural OpenAI voice for a given locale preference.
 *
 * OpenAI's TTS voices have fixed accents — `fable` is the only British
 * option; the rest are American. There's no Australian voice, so en-AU
 * falls back to American `nova` (closest "bright female" timbre) rather
 * than to British `fable`.
 *
 * Used as the *default* when the user hasn't explicitly chosen a voice;
 * once they pick one in Settings the chosen voice wins regardless of locale.
 */
fun defaultOpenAiVoiceFor(voiceLocale: VoiceLocale): String {
    val effective = voiceLocale.resolve()
    val isBritish = effective.language == "en" && effective.country == "GB"
    return if (isBritish) "fable" else "nova"
}
