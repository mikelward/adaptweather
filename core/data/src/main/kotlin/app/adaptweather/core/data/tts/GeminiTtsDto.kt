package app.adaptweather.core.data.tts

import kotlinx.serialization.Serializable

/**
 * Wire types for the Gemini TTS variant of generateContent.
 *
 * The shape is `generateContent` like the text endpoint, but the request specifies
 * `responseModalities: ["AUDIO"]` and a [SpeechConfig], and the response carries an
 * [InlineData] PCM payload instead of plain text. Kept in its own file so the
 * existing text DTOs in `:core:data/insight` stay focused.
 */
@Serializable
internal data class TtsRequest(
    val contents: List<TtsContent>,
    val generationConfig: TtsGenerationConfig,
)

@Serializable
internal data class TtsContent(val parts: List<TtsTextPart>)

@Serializable
internal data class TtsTextPart(val text: String)

@Serializable
internal data class TtsGenerationConfig(
    val responseModalities: List<String> = listOf("AUDIO"),
    val speechConfig: SpeechConfig,
)

@Serializable
internal data class SpeechConfig(val voiceConfig: VoiceConfig)

@Serializable
internal data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoiceConfig)

@Serializable
internal data class PrebuiltVoiceConfig(val voiceName: String)

@Serializable
internal data class TtsResponse(
    val candidates: List<TtsCandidate> = emptyList(),
    val promptFeedback: TtsPromptFeedback? = null,
)

@Serializable
internal data class TtsCandidate(
    val content: TtsCandidateContent? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class TtsCandidateContent(val parts: List<TtsResponsePart> = emptyList())

@Serializable
internal data class TtsResponsePart(
    val inlineData: InlineData? = null,
)

@Serializable
internal data class InlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
internal data class TtsPromptFeedback(
    val blockReason: String? = null,
)
