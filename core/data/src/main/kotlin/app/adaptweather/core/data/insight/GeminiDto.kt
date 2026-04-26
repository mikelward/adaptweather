package app.adaptweather.core.data.insight

import kotlinx.serialization.Serializable

/**
 * Wire types for `generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.
 * Field names match Gemini's REST API (camelCase) so kotlinx.serialization needs no
 * `@SerialName` overrides.
 */
@Serializable
internal data class GenerateContentRequest(
    val systemInstruction: SystemInstruction,
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

@Serializable
internal data class SystemInstruction(val parts: List<Part>)

@Serializable
internal data class Content(val role: String, val parts: List<Part>)

@Serializable
internal data class Part(val text: String)

@Serializable
internal data class GenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
)

@Serializable
internal data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class PromptFeedback(
    val blockReason: String? = null,
)
