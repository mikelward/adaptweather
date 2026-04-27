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
 * Historically a v1 workaround for `tts-1`'s fixed-accent voices: `fable`
 * was the only British-sounding option, so en-GB users defaulted to it.
 * On `gpt-4o-mini-tts` accent is steerable via the request `instructions`
 * field (see `openAiAccentInstructionFor`), but the default-voice choice
 * still matters for first-launch quality of the unconfigured user — pick
 * the voice whose *baseline* timbre is closest to what they'd expect.
 *
 * Used as the *default* when the user hasn't explicitly chosen a voice;
 * once they pick one in Settings the chosen voice wins regardless of locale.
 */
fun defaultOpenAiVoiceFor(voiceLocale: VoiceLocale): String {
    val effective = voiceLocale.resolve()
    val isBritish = effective.language == "en" && effective.country == "GB"
    return if (isBritish) "fable" else "nova"
}
