package com.adaptweather.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * The output of a daily generation pass: a short readable summary suitable for both
 * a notification body and TTS, plus the deterministic list of items the wardrobe rules
 * triggered. The rule-based list is intentionally separate from [summary] so callers
 * can render it without re-parsing LLM text.
 */
data class Insight(
    val summary: String,
    val recommendedItems: List<String>,
    val generatedAt: Instant,
    val forDate: LocalDate,
)
