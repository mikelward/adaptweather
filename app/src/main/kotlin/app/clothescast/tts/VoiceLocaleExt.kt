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
