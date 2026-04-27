package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.Locale

const val DEFAULT_GEMINI_TTS_MODEL: String = "gemini-2.5-flash-preview-tts"
const val DEFAULT_GEMINI_TTS_VOICE: String = "Kore"

internal const val GEMINI_HOST = "generativelanguage.googleapis.com"
internal const val GEMINI_API_VERSION = "v1beta"

/**
 * Natural-language style instruction prepended to every TTS request. Gemini
 * documents the input text as a style-steerable prompt — preambles like this
 * are interpreted as direction and not spoken back. We use it to suppress the
 * lo-fi "vinyl crackle" character the model otherwise bakes into the output
 * across the full clip (OpenAI TTS, going through the same player at the same
 * 24 kHz, has none of it — so this is server-side, not playback-side).
 */
internal const val GEMINI_TTS_STYLE_DIRECTIVE: String =
    "Read the following in a clean, crisp studio voice with no audio effects, " +
        "background noise, or vinyl-style texture:\n\n"

/**
 * Returns a one-line accent instruction for Gemini's TTS prompt, or null when
 * the locale doesn't map to a known English variant (in which case the model's
 * default — North American English — applies).
 *
 * Gemini's prebuilt voices are language-agnostic and accept natural-language
 * accent steering in the same prompt that carries the spoken text. The
 * instruction is prepended to the user text alongside [GEMINI_TTS_STYLE_DIRECTIVE]
 * and is interpreted as direction, not spoken back.
 */
internal fun geminiAccentDirectiveFor(locale: Locale): String? {
    if (locale.language != "en") return null
    return when (locale.country) {
        "GB" -> "Speak with a British English accent."
        "AU" -> "Speak with an Australian English accent."
        "US" -> "Speak with a North American English accent."
        else -> null
    }
}

/**
 * Calls Gemini's audio-output model (e.g. `gemini-2.5-flash-preview-tts`). Uses the
 * standard Generative Language host with a BYOK `x-goog-api-key` header.
 *
 * The model returns a single 16-bit signed PCM audio stream at a sample rate carried
 * in the `mimeType` (`audio/L16;codec=pcm;rate=24000`). [PcmAudio.sampleRate] parses
 * that out of the response so the caller can hand the bytes straight to AudioTrack.
 *
 * Despite the `audio/L16` mime label (which per RFC 2586 specifies network byte
 * order), Gemini's payload is already little-endian — the same encoding Android's
 * `ENCODING_PCM_16BIT` consumes. A previous attempt to byte-swap on decode was
 * reverted because it turned correctly-decoded speech into noise.
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
        locale: Locale? = null,
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException()
        }

        val accent = locale?.let { geminiAccentDirectiveFor(it) }
        val prompt = buildString {
            append(GEMINI_TTS_STYLE_DIRECTIVE)
            if (accent != null) {
                append(accent)
                append("\n\n")
            }
            append(text)
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
                    contents = listOf(
                        TtsContent(parts = listOf(TtsTextPart(prompt))),
                    ),
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
 * deprecated preview model). Pulls just `error.message` out of Gemini's standard
 * error envelope; falls back to a truncated raw excerpt when the body isn't
 * shaped that way.
 */
class GeminiTtsHttpException(val status: HttpStatusCode, body: ByteArray) :
    IllegalStateException(buildMessage(status, body)) {

    companion object {
        private fun buildMessage(status: HttpStatusCode, body: ByteArray): String {
            val raw = runCatching { body.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
            val excerpt = extractErrorMessage(raw)
                ?: raw.take(MAX_EXCERPT_CHARS).ifBlank { "(empty body)" }
            return "Gemini TTS HTTP ${status.value}: $excerpt"
        }

        private fun extractErrorMessage(body: String): String? = runCatching {
            val root = Json.parseToJsonElement(body) as? JsonObject
            val error = root?.get("error") as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content?.take(MAX_EXCERPT_CHARS)
        }.getOrNull()

        private const val MAX_EXCERPT_CHARS = 160
    }
}
