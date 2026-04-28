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
