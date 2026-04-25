package com.adaptweather.insight

/**
 * Curated list of Gemini models surfaced in Settings. Each option pairs the model
 * id (sent to the API) with a short human label.
 *
 * The default written into a fresh install is `gemini-2.5-flash` — see
 * `UserPreferences.DEFAULT_GEMINI_MODEL`. Flash Lite is much cheaper for the daily
 * 25-word output but trades a small amount of quality. Pro is the highest quality
 * at higher latency and cost; reasonable when the user wants more polished prose
 * but probably overkill for the morning insight.
 */
data class GeminiModelOption(val id: String, val displayName: String)

val GEMINI_MODELS: List<GeminiModelOption> = listOf(
    GeminiModelOption("gemini-2.5-flash-lite", "Flash Lite — cheapest, fast"),
    GeminiModelOption("gemini-2.5-flash", "Flash — balanced (default)"),
    GeminiModelOption("gemini-2.5-pro", "Pro — highest quality, slowest"),
)
