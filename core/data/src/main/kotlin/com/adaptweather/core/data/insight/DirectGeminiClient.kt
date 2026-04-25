package com.adaptweather.core.data.insight

import com.adaptweather.core.domain.repository.InsightGenerator
import com.adaptweather.core.domain.usecase.Prompt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path

internal const val GEMINI_HOST = "generativelanguage.googleapis.com"
internal const val GEMINI_API_VERSION = "v1beta"
internal const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

/**
 * BYOK Gemini client: the user's key is read from [keyProvider] (Tink-encrypted
 * DataStore in production) and sent on every call as `x-goog-api-key`. The key
 * never leaves the device.
 *
 * `temperature = 0.4` keeps outputs neutral but not robotic; `maxOutputTokens = 100`
 * caps cost per call and the system instruction caps it to a single 25-word sentence.
 *
 * Thrown errors propagate to the caller (typically a WorkManager worker, which
 * decides whether to retry on network failures or skip the day on auth failures).
 */
class DirectGeminiClient(
    private val httpClient: HttpClient,
    private val keyProvider: KeyProvider,
    private val model: String = DEFAULT_GEMINI_MODEL,
    private val temperature: Double = 0.4,
    private val maxOutputTokens: Int = 100,
) : InsightGenerator {

    override suspend fun generate(prompt: Prompt): String {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException()
        }

        val response: GenerateContentResponse = httpClient.post {
            url {
                protocol = URLProtocol.HTTPS
                host = GEMINI_HOST
                path(GEMINI_API_VERSION, "models", "$model:generateContent")
            }
            header("x-goog-api-key", key)
            contentType(ContentType.Application.Json)
            setBody(
                GenerateContentRequest(
                    systemInstruction = SystemInstruction(parts = listOf(Part(prompt.systemInstruction))),
                    contents = listOf(Content(role = "user", parts = listOf(Part(prompt.userMessage)))),
                    generationConfig = GenerationConfig(
                        temperature = temperature,
                        maxOutputTokens = maxOutputTokens,
                    ),
                ),
            )
        }.body()

        response.promptFeedback?.blockReason?.let {
            throw GeminiBlockedException("Prompt blocked: $it")
        }

        return response.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()
            ?.text
            ?: throw GeminiEmptyResponseException()
    }
}

class GeminiEmptyResponseException : IllegalStateException("Gemini returned no text")

class GeminiBlockedException(message: String) : IllegalStateException(message)
