package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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

// `eleven_turbo_v2_5` is the modern multilingual model — half the credit
// cost of `eleven_multilingual_v2` (0.5 vs. 1.0 credits/char), broader
// language coverage, and noticeably lower first-byte latency. Field reports
// flagged the multilingual_v2 path as "speaking too fast / dropping
// letters" and turbo-v2.5's pacing is more relaxed in practice; combined
// with the speed/stability `voice_settings` we now send, that's a
// meaningful clarity win.
//
// We previously used multilingual_v2 because the Turbo line was positioned
// as a quality-for-latency trade. v2.5 closes that gap — community and
// vendor benchmarks place it at-or-better than multilingual_v2 on stock
// voices, and listening tests for short briefings (1–2 sentences, no
// nuance) don't surface a deficit. Worst case the user notices and picks
// Gemini or device TTS; the engine is BYOK and user-switchable.
//
// Pricing as of April 2026 is plan-based: free tier covers 10k credits/mo
// (no commercial use), Starter $5/mo for 30k, Creator $22/mo for 100k.
// At 0.5 credits/char ClothesCast's two ~100-char clips/day works out to
// ~3k credits/mo — half the multilingual_v2 footprint, well inside the
// free tier on a BYOK key.
const val DEFAULT_ELEVENLABS_TTS_MODEL: String = "eleven_turbo_v2_5"
const val DEFAULT_ELEVENLABS_TTS_VOICE: String = "EXAVITQu4vr4xnSDxMaL" // Sarah

// Wire-IDs for the four models we surface in the picker. Kept as a small set
// (rather than every model ElevenLabs ships) so the radio list stays scannable
// — these are the ones with meaningfully different cost / latency / quality
// trade-offs for short weather briefings.
//
// turbo_v2_5  — current default. 0.5 credits/char, multilingual, low latency,
//               near-multilingual_v2 quality on stock voices.
// multilingual_v2 — 1.0 credits/char (2× the cost), historically the
//               flagship-quality option; some voice clones still tune for it.
// flash_v2_5  — 0.5 credits/char, fastest first-byte, slightly less natural;
//               the "if turbo is still too slow" option.
// v3          — alpha (April 2026). Audio-tag aware. Pricing not yet
//               finalised; surfaced for users who want to experiment.
const val ELEVENLABS_MODEL_TURBO_V2_5: String = "eleven_turbo_v2_5"
const val ELEVENLABS_MODEL_MULTILINGUAL_V2: String = "eleven_multilingual_v2"
const val ELEVENLABS_MODEL_FLASH_V2_5: String = "eleven_flash_v2_5"
const val ELEVENLABS_MODEL_V3: String = "eleven_v3"

// Below ElevenLabs' stock 1.0 because field reports flagged the default pace
// as "too fast" — 0.9 is a noticeable but not draggy slowdown. Stays inside
// the documented 0.7–1.2 range.
const val DEFAULT_ELEVENLABS_TTS_SPEED: Double = 0.9

// Above the stock 0.5 to favour pronunciation consistency over expression
// for short weather briefings — drops fewer consonants. Mirrors
// `core:domain:UserPreferences.DEFAULT_ELEVENLABS_STABILITY`.
const val DEFAULT_ELEVENLABS_TTS_STABILITY: Double = 0.65

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
    private val defaultModel: String = DEFAULT_ELEVENLABS_TTS_MODEL,
) {
    suspend fun synthesize(
        text: String,
        voiceId: String = DEFAULT_ELEVENLABS_TTS_VOICE,
        model: String = defaultModel,
        speed: Double = DEFAULT_ELEVENLABS_TTS_SPEED,
        stability: Double = DEFAULT_ELEVENLABS_TTS_STABILITY,
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
            setBody(
                ElevenLabsSpeechRequest(
                    text = text,
                    modelId = model,
                    voiceSettings = ElevenLabsVoiceSettings(
                        speed = speed,
                        stability = stability,
                    ),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw ElevenLabsTtsHttpException(response.status, response.bodyAsBytes())
        }

        val pcm = response.bodyAsBytes()
        if (pcm.isEmpty()) throw ElevenLabsTtsEmptyResponseException()

        return PcmAudio(bytes = pcm, sampleRate = ELEVENLABS_PCM_SAMPLE_RATE_HZ)
    }

    /**
     * Lists every voice the caller's API key has access to: ElevenLabs'
     * premade library plus any voices the user has cloned, generated, or
     * imported into their account. Used by the Settings refresh button so
     * users with paid plans don't have to copy-paste voice IDs by hand.
     *
     * Returns lightweight [ElevenLabsVoiceSummary] records (id, display
     * name, optional accent label) — translation to the UI's
     * `TtsVoiceOption` happens in the app module to keep this data layer
     * UI-agnostic.
     */
    suspend fun listVoices(): List<ElevenLabsVoiceSummary> {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException("ElevenLabs")
        }
        val response: HttpResponse = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = ELEVENLABS_HOST
                path("v1", "voices")
            }
            header("xi-api-key", key)
        }
        if (!response.status.isSuccess()) {
            throw ElevenLabsTtsHttpException(response.status, response.bodyAsBytes())
        }
        val parsed: ElevenLabsVoicesResponse = response.body()
        return parsed.voices.map { dto ->
            ElevenLabsVoiceSummary(
                id = dto.voiceId,
                name = dto.name,
                accent = dto.labels?.get("accent"),
                description = dto.labels?.get("description"),
            )
        }
    }

    companion object {
        // `pcm_24000` is documented as 16-bit signed little-endian mono at 24 kHz —
        // matches the AudioTrack format we already use for Gemini / OpenAI.
        private const val ELEVENLABS_PCM_SAMPLE_RATE_HZ = 24_000
        private const val PCM_OUTPUT_FORMAT = "pcm_24000"
        private const val ELEVENLABS_HOST = "api.elevenlabs.io"
    }
}

/**
 * Lightweight projection of an ElevenLabs voice — just what the picker
 * needs to render a label. Lives in `core:data` so the wire DTO can stay
 * `internal`; the app module maps these to its UI option type.
 */
data class ElevenLabsVoiceSummary(
    val id: String,
    val name: String,
    val accent: String? = null,
    val description: String? = null,
)

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
