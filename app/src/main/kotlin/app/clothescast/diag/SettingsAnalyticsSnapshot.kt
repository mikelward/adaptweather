package app.clothescast.diag

/**
 * Aggregate-analytics view of the user's language / accent / TTS choices.
 *
 * For each setting we capture three values so reporting breakdowns can tell
 * "user actively picked X" apart from "default fell out as X":
 *  - [_default_]: the value that would apply if the user hasn't overridden it.
 *    For [Region] / [VoiceLocale] this is the BCP-47 tag the SYSTEM sentinel
 *    resolves to on this device; for the remaining settings it's the in-code
 *    default (DEVICE / NORMAL / "Erinome" / "AUTO").
 *  - [_override_]: the user's persisted choice. [UNSET] when no DataStore key
 *    exists; otherwise the enum name for [Region] / [VoiceLocale] / [TtsEngine]
 *    / [TtsStyle], or the raw string for the voice IDs. The SYSTEM enum entry
 *    on [Region] / [VoiceLocale] communicates "user picked SYSTEM explicitly"
 *    — same observable behaviour as UNSET, but distinguishable in reports.
 *  - [_effective_]: the value actually in use (override resolved against
 *    default).
 *
 * Every value is a short configuration string — enum name, BCP-47 tag, or
 * voice ID. No user content, no identifiers, so it fits the analytics
 * contract in PRIVACY.md.
 */
data class SettingsAnalyticsSnapshot(
    val regionDefault: String,
    val regionOverride: String,
    val regionEffective: String,
    val voiceLocaleDefault: String,
    val voiceLocaleOverride: String,
    val voiceLocaleEffective: String,
    val ttsEngineDefault: String,
    val ttsEngineOverride: String,
    val ttsEngineEffective: String,
    val ttsStyleDefault: String,
    val ttsStyleOverride: String,
    val ttsStyleEffective: String,
    val geminiVoiceDefault: String,
    val geminiVoiceOverride: String,
    val geminiVoiceEffective: String,
    val deviceVoiceDefault: String,
    val deviceVoiceOverride: String,
    val deviceVoiceEffective: String,
) {
    companion object {
        /** No value persisted in DataStore for this setting. */
        const val UNSET = "UNSET"

        /**
         * On-device TTS auto-pick — the speaker resolves the highest-quality
         * voice for the effective locale at speak time. Used for the device
         * voice's default and for its effective value when no pin is set.
         */
        const val AUTO = "AUTO"
    }
}
