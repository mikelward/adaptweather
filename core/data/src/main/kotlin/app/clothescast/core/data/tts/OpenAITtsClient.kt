package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// `gpt-4o-mini-tts` is OpenAI's current TTS-of-record (March 2025). The
// upgrade from `tts-1` is a per-clip-quality win — positioned by OpenAI as
// comparable to `tts-1-hd`. Pricing, as of April 2026, is $0.60/M text
// input tokens + $12/M audio output tokens, which works out to ~$0.015/min
// of audio (roughly 35-40% more than tts-1's per-character rate); for
// ClothesCast's two-clips-a-day usage that's ~$0.003/day, ~$0.09/month on
// a BYOK key.
//
// The `instructions` field has a split personality on this model:
//   - *accent* steering doesn't land — earlier field-testing confirmed the
//     voice's baked-in accent dominates regardless of directive. Voice-list
//     filtering in `TtsVoices` is the mechanism for giving the user a
//     non-American voice (just `fable`, the only British in the stock list).
//   - *tone* and *pace* steering does land — directives like "speak clearly
//     at a measured pace" measurably slow the synthesis and reduce dropped
//     consonants on field tests. We also steer toward a friendly-announcement
//     tone to avoid the theatrical delivery the model defaults to. The
//     directive is a constant, not locale-aware, since the goal is
//     "clear, upbeat, and grounded in any language" rather than accent-shaping.
const val DEFAULT_OPENAI_TTS_MODEL: String = "gpt-4o-mini-tts"
const val DEFAULT_OPENAI_TTS_VOICE: String = "alloy"
// Mirrors `core:domain:UserPreferences.DEFAULT_OPENAI_SPEED` — the API's
// stock 1.0× pace. Duplicated here so this layer can stay domain-free.
const val DEFAULT_OPENAI_TTS_SPEED: Double = 1.0

private const val TONE_INSTRUCTIONS: String =
    "Speak as a clear, well-enunciated, slightly upbeat announcement — like a friendly " +
        "morning radio host or public address system. Speak with energy and confidence " +
        "so it grabs attention, but keep the tone grounded and conversational. " +
        "Not theatrical, not dramatic, not cinematic."

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
        speed: Double = DEFAULT_OPENAI_TTS_SPEED,
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException("OpenAI")
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
                    instructions = TONE_INSTRUCTIONS,
                    // Only send a non-default value to keep the request body
                    // minimal and to avoid the (slim) chance that the field
                    // changes the model's behaviour at exactly 1.0.
                    speed = speed.takeIf { it != DEFAULT_OPENAI_TTS_SPEED },
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
 * Pulls just `error.message` out of OpenAI's standard error envelope when
 * present; falls back to a truncated raw excerpt otherwise.
 */
class OpenAITtsHttpException(val status: HttpStatusCode, body: ByteArray) :
    IllegalStateException(buildMessage(status, body)) {

    companion object {
        private fun buildMessage(status: HttpStatusCode, body: ByteArray): String {
            val raw = runCatching { body.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
            val excerpt = extractErrorMessage(raw)
                ?: raw.take(MAX_EXCERPT_CHARS).ifBlank { "(empty body)" }
            return "OpenAI TTS HTTP ${status.value}: $excerpt"
        }

        private fun extractErrorMessage(body: String): String? = runCatching {
            val root = Json.parseToJsonElement(body) as? JsonObject
            val error = root?.get("error") as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content?.take(MAX_EXCERPT_CHARS)
        }.getOrNull()

        private const val MAX_EXCERPT_CHARS = 160
    }
}
