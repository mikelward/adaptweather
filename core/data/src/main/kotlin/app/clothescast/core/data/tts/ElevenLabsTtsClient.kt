package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// `eleven_multilingual_v2` is ElevenLabs' "1 character = 1 credit" model
// (their Flash/Turbo variants run at 0.5 credits/char but trade quality
// for latency, which we don't need for an asynchronous morning brief).
// Pricing is plan-based rather than purely metered: the free tier covers
// 10k credits/month (no commercial use), Starter is $5/mo for 30k credits,
// Creator $22/mo for 100k. ClothesCast's two ~100-char clips/day works out
// to ~6k credits/month — comfortably inside the free tier and ~5% of
// Starter, so on a BYOK key the per-clip marginal cost is effectively zero
// until the user overruns their plan's allotment.
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

        val response: HttpResponse = httpClient.post {
            url {
                protocol = URLProtocol.HTTPS
                host = ELEVENLABS_HOST
                // Use Ktor's path-segment builder so `voiceId` is treated as a
                // single segment and reserved characters (/, ?, #, %) are
                // percent-encoded — voice IDs come from persisted user input,
                // and a stray slash would otherwise silently mis-route.
                path("v1", "text-to-speech", voiceId)
            }
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
        private const val ELEVENLABS_HOST = "api.elevenlabs.io"
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
