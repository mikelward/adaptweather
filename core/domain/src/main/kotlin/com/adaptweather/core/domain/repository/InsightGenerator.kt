package com.adaptweather.core.domain.repository

import com.adaptweather.core.domain.usecase.Prompt

/**
 * Abstract LLM call. Implementations live in :core:data (DirectGeminiClient for v1's
 * BYOK path; a ProxyGeminiClient when a backend exists). Returning a plain String keeps
 * the interface free of vendor types so domain remains KMP-clean.
 */
interface InsightGenerator {
    suspend fun generate(prompt: Prompt): String
}
