package app.adaptweather.core.data.tts

import app.adaptweather.core.data.insight.KeyProvider
import app.adaptweather.core.data.insight.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val DEFAULT_ELEVENLABS_TTS_MODEL: String = "eleven_multilingual_v2"
const val DEFAULT_ELEVENLABS_TTS_VOICE: String = "EXAVITQu4vr4xnSDxMaL" // Sarah

/**
 * ElevenLabs TTS via `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}`.
 *
 * Asks for `output_format=pcm_24000` so the response body is raw 16-bit signed
 * little-endian mono PCM at 24 kHz — bit-compatible with the Gemini and OpenAI
 * TTS paths and with the same [PcmAudio] structure, so playback reuses the
 * existing AudioTrack code on the app side.
 *
 * Auth is the user's ElevenLabs key as the `xi-api-key` header (not a Bearer
 * token, unlike OpenAI). Like the other providers the key never leaves the
 * device.
 */
class ElevenLabsTtsClient(
    private val httpClient: HttpClient,
    private val keyProvider: KeyProvider,
    private val model: String = DEFAULT_ELEVENLABS_TTS_MODEL,
) {
    suspend fun synthesize(
        text: String,
        voiceId: String = DEFAULT_ELEVENLABS_TTS_VOICE,
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException("ElevenLabs")
        }

        val response: HttpResponse = httpClient.post("$SPEECH_URL_BASE/$voiceId") {
            header("xi-api-key", key)
            contentType(ContentType.Application.Json)
            parameter("output_format", PCM_OUTPUT_FORMAT)
            setBody(ElevenLabsSpeechRequest(text = text, modelId = model))
        }

        if (!response.status.isSuccess()) {
            throw ElevenLabsTtsHttpException(response.status, response.bodyAsBytes())
        }

        val pcm = response.bodyAsBytes()
        if (pcm.isEmpty()) throw ElevenLabsTtsEmptyResponseException()

        return PcmAudio(bytes = pcm, sampleRate = ELEVENLABS_PCM_SAMPLE_RATE_HZ)
    }

    companion object {
        // `pcm_24000` is documented as 16-bit signed little-endian mono at 24 kHz —
        // matches the AudioTrack format we already use for Gemini / OpenAI.
        private const val ELEVENLABS_PCM_SAMPLE_RATE_HZ = 24_000
        private const val PCM_OUTPUT_FORMAT = "pcm_24000"
        private const val SPEECH_URL_BASE = "https://api.elevenlabs.io/v1/text-to-speech"
    }
}

class ElevenLabsTtsEmptyResponseException :
    IllegalStateException("ElevenLabs TTS returned no audio body")

/**
 * HTTP failure surfaced with a short body excerpt so the diagnostic Toast in
 * Settings shows the actual reason (auth failure / quota / unknown voice ID).
 * Pulls just `detail.message` (or `detail` itself when it's a string) out of
 * ElevenLabs's standard error envelope; falls back to a truncated raw excerpt
 * otherwise.
 */
class ElevenLabsTtsHttpException(val status: HttpStatusCode, body: ByteArray) :
    IllegalStateException(buildMessage(status, body)) {

    companion object {
        private fun buildMessage(status: HttpStatusCode, body: ByteArray): String {
            val raw = runCatching { body.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
            val excerpt = extractErrorMessage(raw)
                ?: raw.take(MAX_EXCERPT_CHARS).ifBlank { "(empty body)" }
            return "ElevenLabs TTS HTTP ${status.value}: $excerpt"
        }

        private fun extractErrorMessage(body: String): String? = runCatching {
            val root = Json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            // ElevenLabs envelopes are usually `{"detail": {"status": "...", "message": "..."}}`
            // but on validation failures `detail` is a plain string. Handle both.
            val detail = root["detail"]
            when (detail) {
                is JsonObject ->
                    (detail["message"] as? JsonPrimitive)?.content?.take(MAX_EXCERPT_CHARS)
                is JsonPrimitive ->
                    detail.content.takeIf { detail.isString }?.take(MAX_EXCERPT_CHARS)
                else -> null
            }
        }.getOrNull()

        private const val MAX_EXCERPT_CHARS = 160
    }
}
