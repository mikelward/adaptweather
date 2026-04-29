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

// `gemini-2.5-flash-preview-tts` is the audio-output variant of Flash 2.5
// (text-only Flash 2.5 is much cheaper but doesn't speak). Pricing as of
// April 2026: standard-tier pricing is $1.00/M text input tokens + $20.00/M
// audio output tokens; a typical ~100-char insight ≈ 25 input tokens + ~160
// audio output tokens for a ~5 s clip, which works out to ~$0.003 per call
// (~$0.20/month at two clips/day). The Gemini free tier covers low-volume
// BYOK use without billing at all. Note this model is still on the
// `-preview-` track — see CLAUDE.md's "Don't rename Gemini models from
// web-search guesses" before swapping it for a GA-sounding name.
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
 * Returns a one-line accent / language instruction for Gemini's TTS prompt, or
 * null when the locale has no entry in [ACCENT_DIRECTIVES] (in which case the
 * model's default — North American English — applies).
 *
 * Gemini's prebuilt voices are language-agnostic and accept natural-language
 * accent steering in the same prompt that carries the spoken text. The
 * instruction is prepended to the user text alongside [GEMINI_TTS_STYLE_DIRECTIVE]
 * and is interpreted as direction, not spoken back.
 *
 * Lookup is two-step: an exact `language-COUNTRY` match wins (so variants like
 * en-GB / en-AU / en-US can carry their own directive), otherwise a
 * language-only key. Today the table holds only `language-COUNTRY` entries —
 * the language-only branch exists for a future `de` / `fr` / etc. directive
 * that follows the same shape. Adding a new locale is a one-line entry in
 * [ACCENT_DIRECTIVES].
 */
internal fun geminiAccentDirectiveFor(locale: Locale): String? {
    val country = locale.country
    if (country.isNotEmpty()) {
        ACCENT_DIRECTIVES["${locale.language}-$country"]?.let { return it }
    }
    return ACCENT_DIRECTIVES[locale.language]
}

private val ACCENT_DIRECTIVES: Map<String, String> = mapOf(
    "en-GB" to "Speak with a Standard Southern British accent.",
    "en-AU" to "Speak with a General Australian accent.",
    "en-US" to "Speak with a General American accent.",
    "en-CA" to "Speak with a Canadian English accent.",
    // Language-only entry — Gemini follows the directive *language* even though
    // the prompt itself is English, so the synthesised audio is in German. The
    // German prose comes from the `:app` formatter once the user's Region is
    // set to DE_DE; this directive just nudges the model to read it as German
    // rather than mangling it through an English phoneme pipeline.
    "de" to "Sprich auf Deutsch in einem klaren, hochdeutschen Akzent.",
    "fr-FR" to "Parle en français de France avec un accent parisien clair.",
    "fr-CA" to "Parle en français québécois avec un accent canadien-français.",
    "it" to "Parla in italiano con un accento italiano chiaro e naturale.",
    "es-ES" to "Habla en español de España con un acento castellano claro.",
    "es-MX" to "Habla en español mexicano con un acento neutro y claro.",
    "ru" to "Говори по-русски с чётким литературным произношением.",
    "pl" to "Mów po polsku z wyraźną i naturalną wymową.",
    "hr" to "Govori na hrvatskom jeziku s jasnim i prirodnim izgovorom.",
    "el" to "Μίλα στα ελληνικά με καθαρή και φυσική προφορά.",
    "uk" to "Говори українською мовою з чітким літературним вимовленням.",
    "pt-BR" to "Fale em português brasileiro com sotaque neutro e claro.",
    "nl" to "Spreek in het Nederlands met een duidelijk en natuurlijk accent.",
    "sv" to "Tala på svenska med ett tydligt och naturligt uttal.",
    "tr" to "Türkçe konuş, net ve doğal bir Türkçe aksanıyla.",
    "en-ZA" to "Speak with a South African English accent.",
    "id" to "Bicara dalam bahasa Indonesia dengan aksen yang jelas dan alami.",
    "fil" to "Magsalita sa Filipino na may malinaw at natural na diin.",
    "vi" to "Nói tiếng Việt với giọng điệu rõ ràng và tự nhiên.",
    "th" to "กรุณาอ่านเป็นภาษาไทยด้วยน้ำเสียงที่ชัดเจนและเป็นธรรมชาติ",
    "zh-CN" to "请用标准普通话朗读，发音清晰自然。",
    // Language-only fallback covers generic zh locale and any zh-* variant
    // not explicitly listed (zh-TW, zh-HK, etc.) — all are Mandarin-readable.
    "zh" to "请用标准普通话朗读，发音清晰自然。",
    "hi" to "कृपया हिंदी में स्पष्ट और प्राकृतिक उच्चारण के साथ पढ़ें।",
    "bn" to "অনুগ্রহ করে বাংলায় স্পষ্ট ও প্রাকৃতিক উচ্চারণে পড়ুন।",
    "ja" to "はっきりと自然な発音で日本語で読んでください。",
    "ko" to "명확하고 자연스러운 발음으로 한국어로 읽어주세요。",
    // Arabic: directives nudge the model to read MSA prose with the named
    // country's accent (Egyptian / Saudi / Emirati / Moroccan), not to
    // code-switch to the colloquial dialect — the :app formatter produces
    // MSA, so a code-switch instruction would mismatch the input. The bare
    // `ar` entry stays as the fallback for any future ar-* locale we don't
    // explicitly enumerate. Whether Gemini's audio model reliably produces
    // a recognisably-different accent per country for Arabic is empirical
    // and worth a listening test (it does so well for English / Spanish /
    // Mandarin variants; community reports for Arabic are mixed). Worst
    // case it ignores the country qualifier and we get generic MSA — same
    // as before the split, no regression.
    "ar-SA" to "اقرأ النص التالي بالعربية الفصحى بنطق سعودي واضح وطبيعي.",
    "ar-EG" to "اقرأ النص التالي بالعربية الفصحى بنطق مصري واضح وطبيعي.",
    "ar-AE" to "اقرأ النص التالي بالعربية الفصحى بنطق إماراتي واضح وطبيعي.",
    "ar-MA" to "اقرأ النص التالي بالعربية الفصحى بنطق مغربي واضح وطبيعي.",
    "ar" to "اقرأ النص التالي بالعربية بنطق واضح وطبيعي.",
    // Hebrew / Persian remain language-only — only he-IL / fa-IR are offered
    // in the picker, so there's nothing to disambiguate.
    "he" to "קרא/י את הטקסט הבא בעברית בהגייה ברורה וטבעית.",
    "fa" to "متن زیر را به فارسی با تلفظ واضح و طبیعی بخوانید.",
)

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
