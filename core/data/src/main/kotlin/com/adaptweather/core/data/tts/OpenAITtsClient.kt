package com.adaptweather.core.data.tts

import com.adaptweather.core.data.insight.KeyProvider
import com.adaptweather.core.data.insight.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

const val DEFAULT_OPENAI_TTS_MODEL: String = "tts-1"
const val DEFAULT_OPENAI_TTS_VOICE: String = "alloy"

/**
 * OpenAI text-to-speech via `https://api.openai.com/v1/audio/speech`.
 *
 * Asks for `response_format: "pcm"` so the response body is raw 16-bit signed
 * little-endian mono PCM at 24 kHz — bit-compatible with Gemini's TTS output and
 * with the same [PcmAudio] structure, so playback reuses the existing AudioTrack
 * path on the app side.
 *
 * Auth is the user's OpenAI key as a `Bearer` token. Like the Gemini path the key
 * never leaves the device.
 */
class OpenAITtsClient(
    private val httpClient: HttpClient,
    private val keyProvider: KeyProvider,
    private val model: String = DEFAULT_OPENAI_TTS_MODEL,
) {
    suspend fun synthesize(
        text: String,
        voice: String = DEFAULT_OPENAI_TTS_VOICE,
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException()
        }

        val response: HttpResponse = httpClient.post(SPEECH_URL) {
            header("Authorization", "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(
                OpenAISpeechRequest(
                    model = model,
                    input = text,
                    voice = voice,
                    responseFormat = "pcm",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw OpenAITtsHttpException(response.status, response.bodyAsBytes())
        }

        val pcm = response.bodyAsBytes()
        if (pcm.isEmpty()) throw OpenAITtsEmptyResponseException()

        return PcmAudio(bytes = pcm, sampleRate = OPENAI_PCM_SAMPLE_RATE_HZ)
    }

    companion object {
        // OpenAI's PCM response format is documented as 24000 Hz, 16-bit signed
        // little-endian, mono — fixed, not negotiable, and not reflected in any
        // response header. Hard-coded here.
        private const val OPENAI_PCM_SAMPLE_RATE_HZ = 24_000
        private const val SPEECH_URL = "https://api.openai.com/v1/audio/speech"
    }
}

class OpenAITtsEmptyResponseException :
    IllegalStateException("OpenAI TTS returned no audio body")

/**
 * HTTP failure surfaced with a short body excerpt so the diagnostic Toast in
 * Settings shows the actual reason (auth failure / quota / model unavailable).
 */
class OpenAITtsHttpException(val status: HttpStatusCode, body: ByteArray) :
    IllegalStateException(buildMessage(status, body)) {

    companion object {
        private fun buildMessage(status: HttpStatusCode, body: ByteArray): String {
            val excerpt = runCatching { body.toString(Charsets.UTF_8) }
                .getOrDefault("(unparseable body)")
                .take(MAX_EXCERPT_CHARS)
            return "OpenAI TTS HTTP ${status.value}: $excerpt"
        }

        private const val MAX_EXCERPT_CHARS = 240
    }
}
