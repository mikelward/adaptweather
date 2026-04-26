package app.adaptweather.core.data.tts

import app.adaptweather.core.data.insight.GEMINI_API_VERSION
import app.adaptweather.core.data.insight.GEMINI_HOST
import app.adaptweather.core.data.insight.KeyProvider
import app.adaptweather.core.data.insight.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import java.util.Base64

const val DEFAULT_GEMINI_TTS_MODEL: String = "gemini-2.5-flash-preview-tts"
const val DEFAULT_GEMINI_TTS_VOICE: String = "Kore"

/**
 * Calls Gemini's audio-output model (e.g. `gemini-2.5-flash-preview-tts`). Same host,
 * same auth header, same BYOK key as [app.adaptweather.core.data.insight.DirectGeminiClient].
 *
 * The model returns a single 16-bit signed PCM audio stream at a sample rate carried
 * in the `mimeType` (`audio/L16;codec=pcm;rate=24000`). [PcmAudio.sampleRate] parses
 * that out of the response so the caller can hand the bytes straight to AudioTrack.
 *
 * Default voice is `Kore` (firm). Other prebuilt voices are listed in Google's docs;
 * pass via [voiceName] when calling.
 */
class GeminiTtsClient(
    private val httpClient: HttpClient,
    private val keyProvider: KeyProvider,
    private val model: String = DEFAULT_GEMINI_TTS_MODEL,
) {
    suspend fun synthesize(
        text: String,
        voiceName: String = DEFAULT_GEMINI_TTS_VOICE,
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException()
        }

        val httpResponse: HttpResponse = httpClient.post {
            url {
                protocol = URLProtocol.HTTPS
                host = GEMINI_HOST
                path(GEMINI_API_VERSION, "models", "$model:generateContent")
            }
            header("x-goog-api-key", key)
            contentType(ContentType.Application.Json)
            setBody(
                TtsRequest(
                    contents = listOf(TtsContent(parts = listOf(TtsTextPart(text)))),
                    generationConfig = TtsGenerationConfig(
                        responseModalities = listOf("AUDIO"),
                        speechConfig = SpeechConfig(
                            voiceConfig = VoiceConfig(
                                prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName),
                            ),
                        ),
                    ),
                ),
            )
        }

        // Without an explicit status check we'd quietly deserialize a 4xx error body
        // (e.g. {"error": {"code": 403, "message": "..."}}) as a TtsResponse with
        // default empty `candidates`, hiding the actual reason behind the generic
        // empty-response exception. Mirror OpenAITtsClient's approach: pull the body
        // on a non-success status, surface it.
        if (!httpResponse.status.isSuccess()) {
            throw GeminiTtsHttpException(httpResponse.status, httpResponse.bodyAsBytes())
        }

        val response: TtsResponse = httpResponse.body()

        response.promptFeedback?.blockReason?.let {
            throw GeminiTtsBlockedException("Gemini TTS prompt blocked: $it")
        }

        val candidate = response.candidates.firstOrNull()
        val inline = candidate?.content?.parts?.firstOrNull { it.inlineData != null }
            ?.inlineData
            ?: throw GeminiTtsEmptyResponseException(candidate?.finishReason)

        val pcm = Base64.getDecoder().decode(inline.data)
        val sampleRate = parseSampleRate(inline.mimeType) ?: DEFAULT_SAMPLE_RATE_HZ
        return PcmAudio(bytes = pcm, sampleRate = sampleRate)
    }

    private fun parseSampleRate(mimeType: String): Int? {
        // mimeType looks like "audio/L16;codec=pcm;rate=24000". Pull the rate token out
        // rather than assume — the model could change defaults.
        return mimeType.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("rate=") }
            ?.removePrefix("rate=")
            ?.toIntOrNull()
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 24_000
    }
}

/** Decoded TTS audio: signed 16-bit PCM, mono, at [sampleRate] Hz. */
data class PcmAudio(val bytes: ByteArray, val sampleRate: Int) {
    override fun equals(other: Any?): Boolean =
        other is PcmAudio && bytes.contentEquals(other.bytes) && sampleRate == other.sampleRate

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + sampleRate
}

class GeminiTtsEmptyResponseException(finishReason: String?) :
    IllegalStateException(
        if (finishReason.isNullOrBlank()) {
            "Gemini TTS returned no inline-audio part"
        } else {
            "Gemini TTS returned no inline-audio part (finishReason=$finishReason)"
        },
    )

class GeminiTtsBlockedException(message: String) : IllegalStateException(message)

/**
 * HTTP failure surfaced with a short body excerpt so the diagnostic Toast in
 * Settings shows the actual reason (auth failure / quota / model unavailable /
 * deprecated preview model). Without this, Ktor would deserialize the error JSON
 * as a TtsResponse with default empty candidates and we'd report the generic
 * empty-response error with no diagnostic.
 */
class GeminiTtsHttpException(val status: HttpStatusCode, body: ByteArray) :
    IllegalStateException(buildMessage(status, body)) {

    companion object {
        private fun buildMessage(status: HttpStatusCode, body: ByteArray): String {
            val excerpt = runCatching { body.toString(Charsets.UTF_8) }
                .getOrDefault("(unparseable body)")
                .take(MAX_EXCERPT_CHARS)
            return "Gemini TTS HTTP ${status.value}: $excerpt"
        }

        private const val MAX_EXCERPT_CHARS = 240
    }
}
