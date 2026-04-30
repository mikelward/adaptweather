package app.clothescast.tts

import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.VoiceLocale
import java.util.Locale

/**
 * Maps a [VoiceLocale] preference to a concrete [Locale].
 *
 * Returns null for [VoiceLocale.SYSTEM] so callers can fall back to
 * the app's configured [Region] (or ultimately `Locale.getDefault()`)
 * themselves — keeps the "follow the app language" semantics deferred
 * to the call site instead of baking it in here.
 */
fun VoiceLocale.toJavaLocale(): Locale? = bcp47?.let { Locale.forLanguageTag(it) }

/**
 * Maps a [Region] preference to a concrete [Locale].
 *
 * Returns null for [Region.SYSTEM] so callers can fall back to
 * `Locale.getDefault()` themselves.
 */
fun Region.toJavaLocale(): Locale? = bcp47?.let { Locale.forLanguageTag(it) }

/**
 * Resolves the effective [Locale] for a [VoiceLocale] preference.
 *
 * [VoiceLocale.SYSTEM] means "follow the app language" — use [fallback],
 * which should be the app's configured [Region] locale (or `Locale.getDefault()`
 * when the region is also SYSTEM). Explicit locale values resolve directly from
 * their BCP-47 tag, ignoring [fallback].
 */
fun VoiceLocale.resolve(fallback: Locale = Locale.getDefault()): Locale = toJavaLocale() ?: fallback

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
 * [fallback] is used when [voiceLocale] is [VoiceLocale.SYSTEM] — pass the
 * app's configured region locale so a user whose app language is en-GB gets
 * `fable` without having to explicitly set their accent to en-GB.
 *
 * Used as the *default* when the user hasn't explicitly chosen a voice;
 * once they pick one in Settings the chosen voice wins regardless of locale.
 */
fun defaultOpenAiVoiceFor(voiceLocale: VoiceLocale, fallback: Locale = Locale.getDefault()): String {
    val effective = voiceLocale.resolve(fallback)
    val isBritish = effective.language == "en" && effective.country == "GB"
    return if (isBritish) "fable" else "nova"
}
