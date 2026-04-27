package app.clothescast.tts

import app.clothescast.core.domain.model.VoiceLocale
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
 * OpenAI's stock voices have *fixed accents* that prompt-side instructions
 * can't reliably override, so the right voice is the one whose baseline
 * timbre matches the user's variant. `fable` is the only British-sounding
 * option in the curated list; everything else sounds American. There's no
 * Australian voice — en-AU users default to `nova` (the same default as
 * en-US) and can pick from the full list in Settings.
 *
 * Used as the *default* when the user hasn't explicitly chosen a voice;
 * once they pick one in Settings the chosen voice wins regardless of locale.
 */
fun defaultOpenAiVoiceFor(voiceLocale: VoiceLocale): String {
    val effective = voiceLocale.resolve()
    val isBritish = effective.language == "en" && effective.country == "GB"
    return if (isBritish) "fable" else "nova"
}
