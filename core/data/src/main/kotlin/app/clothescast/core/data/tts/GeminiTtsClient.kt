package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import app.clothescast.core.domain.model.TtsStyle
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
// TODO: decide default voice — top candidates from style eval are Aoede,
//  Charon, Kore (all avg 8.6 on B1+B2 combined). Current default Leda is
//  6th overall and untested under the updated accent directives.
const val DEFAULT_GEMINI_TTS_VOICE: String = "Leda"

internal const val GEMINI_HOST = "generativelanguage.googleapis.com"
internal const val GEMINI_API_VERSION = "v1beta"

/**
 * Natural-language style instructions prepended to every TTS request. Gemini
 * documents the input text as a style-steerable prompt — preambles like this
 * are interpreted as direction and not spoken back.
 *
 * [GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER] is the default register —
 * national-news broadcast delivery, deliberate cadence, sentence-final lift,
 * gentle emphasis on clothing advice. Chosen via listening eval over a neutral
 * "studio voice" and a newsreader variant.
 *
 * Below the default are character / persona registers (pirate, cowboy, etc.).
 * Each one starts with "Read the following…" so the model treats it as
 * delivery direction rather than a rewrite, permits brief in-character
 * interjections, and ends with the standard "no audio effects, background
 * noise, or vinyl-style texture" trailer — important for personas like
 * PIRATE that might otherwise tempt the model to add ocean ambience.
 */
internal const val GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER: String =
    "Read the following weather forecast in the style of a weather report on a " +
        "national news service. Enunciate clearly and use a measured speed. " +
        "Accentuate the ends of sentences and give a gentle emphasis to clothing " +
        "recommendations. No audio effects, background noise, or vinyl-style " +
        "texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_PIRATE: String =
    "Read the following as a swaggering pirate, with hearty nautical flair. " +
        "You may add brief in-character exclamations such as 'Arrr' or 'Aye, " +
        "matey'. No audio effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_COWBOY: String =
    "Read the following in a slow, friendly American Old West drawl. You may " +
        "add brief in-character exclamations such as 'Howdy' or 'Well, partner'. " +
        "No audio effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_SHAKESPEAREAN: String =
    "Read the following in the theatrical register of an Elizabethan stage " +
        "actor — measured cadence, rolling vowels. You may add brief " +
        "in-character interjections such as 'Hark' or 'Forsooth'. No audio " +
        "effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_SURFER: String =
    "Read the following as a laid-back Southern California surfer — relaxed " +
        "pace, easy enthusiasm. You may add brief in-character interjections " +
        "such as 'Dude' or 'Totally'. No audio effects, background noise, or " +
        "vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_PARENT: String =
    "Read the following as a warm, encouraging parent gently reminding their " +
        "kid before they head out. Calm, caring; brief endearments are fine. " +
        "No audio effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_CHILD: String =
    "Read the following as an excited young child sharing news with a friend " +
        "— bright, eager, simple inflection. You may add brief in-character " +
        "exclamations such as 'Wow!' or 'Cool!'. No audio effects, background " +
        "noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_TEENAGER: String =
    "Read the following as a slightly bored modern teenager — flat-ish " +
        "affect, occasional rising inflection. You may add brief in-character " +
        "interjections such as 'literally' or 'I mean…'. No audio effects, " +
        "background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_GRANDPARENT: String =
    "Read the following as a kindly grandparent passing on advice — " +
        "unhurried, warm, slightly wry. Brief endearments are fine. No audio " +
        "effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_KIDS_HOST: String =
    "Read the following as the bright, animated host of a children's TV show " +
        "— high energy, big smiles in the voice, simple vocabulary cadence. " +
        "Brief enthusiastic interjections welcome. No audio effects, " +
        "background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_RADIO_HOST: String =
    "Read the following as a chatty talkback radio host addressing the " +
        "audience — conversational, opinionated, with a touch of warmth. " +
        "Brief asides are fine. No audio effects, background noise, or " +
        "vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_MORNING_DJ: String =
    "Read the following as an upbeat breakfast-radio DJ — bright, energetic, " +
        "chatty. Brief interjections such as 'alright!' or 'here's your " +
        "forecast' are fine. No audio effects, background noise, or " +
        "vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_SCIENCE_TEACHER: String =
    "Read the following as an enthusiastic high school science teacher " +
        "walking the class through today's weather reasoning — clear, " +
        "curious, mildly excitable about the physics. No audio effects, " +
        "background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_HISTORIAN: String =
    "Read the following as the narrator of a serious history documentary — " +
        "measured, gravely interested, every sentence matters. No audio " +
        "effects, background noise, or vinyl-style texture:\n\n"

internal const val GEMINI_TTS_STYLE_DIRECTIVE_SPORTSCASTER: String =
    "Read the following as an animated sportscaster calling a play — " +
        "building energy, crisp emphasis on the key facts. Brief interjections " +
        "are fine. No audio effects, background noise, or vinyl-style " +
        "texture:\n\n"

/**
 * Resolves the style preamble for [style], honouring [customDirective] when
 * the user has picked [TtsStyle.CUSTOM]. A blank custom directive falls back
 * to [GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER] so a half-typed CUSTOM
 * doesn't break TTS for the user — they get a normal-sounding read and the
 * chance to fill the field in.
 */
internal fun styleDirectiveFor(
    style: TtsStyle,
    customDirective: String = "",
): String = when (style) {
    TtsStyle.WEATHER_FORECASTER -> GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER
    TtsStyle.PIRATE -> GEMINI_TTS_STYLE_DIRECTIVE_PIRATE
    TtsStyle.COWBOY -> GEMINI_TTS_STYLE_DIRECTIVE_COWBOY
    TtsStyle.SHAKESPEAREAN -> GEMINI_TTS_STYLE_DIRECTIVE_SHAKESPEAREAN
    TtsStyle.SURFER -> GEMINI_TTS_STYLE_DIRECTIVE_SURFER
    TtsStyle.PARENT -> GEMINI_TTS_STYLE_DIRECTIVE_PARENT
    TtsStyle.CHILD -> GEMINI_TTS_STYLE_DIRECTIVE_CHILD
    TtsStyle.TEENAGER -> GEMINI_TTS_STYLE_DIRECTIVE_TEENAGER
    TtsStyle.GRANDPARENT -> GEMINI_TTS_STYLE_DIRECTIVE_GRANDPARENT
    TtsStyle.KIDS_HOST -> GEMINI_TTS_STYLE_DIRECTIVE_KIDS_HOST
    TtsStyle.RADIO_HOST -> GEMINI_TTS_STYLE_DIRECTIVE_RADIO_HOST
    TtsStyle.MORNING_DJ -> GEMINI_TTS_STYLE_DIRECTIVE_MORNING_DJ
    TtsStyle.SCIENCE_TEACHER -> GEMINI_TTS_STYLE_DIRECTIVE_SCIENCE_TEACHER
    TtsStyle.HISTORIAN -> GEMINI_TTS_STYLE_DIRECTIVE_HISTORIAN
    TtsStyle.SPORTSCASTER -> GEMINI_TTS_STYLE_DIRECTIVE_SPORTSCASTER
    TtsStyle.CUSTOM -> customDirective.trim().takeIf { it.isNotEmpty() }
        ?.let { "$it\n\n" }
        ?: GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER
}

/**
 * Returns a one-line accent / language instruction for Gemini's TTS prompt, or
 * null when the locale has no entry in [ACCENT_DIRECTIVES] (in which case the
 * model's default — North American English — applies).
 *
 * Gemini's prebuilt voices are language-agnostic and accept natural-language
 * accent steering in the same prompt that carries the spoken text. The
 * instruction is prepended to the user text alongside the chosen style
 * directive (see [styleDirectiveFor]) and is interpreted as direction, not
 * spoken back.
 *
 * Lookup is two-step: an exact `language-COUNTRY` match wins (so variants like
 * en-GB / en-AU / en-US can carry their own directive), otherwise a
 * language-only key. The table mixes both shapes — most non-English entries
 * are language-only (`de`, `fr`, `vi`, …), while English and Arabic
 * enumerate per-country variants. Adding a new locale is a one-line entry in
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
    "en-GB" to "Speak with a Standard British accent — clear and natural.",
    "en-AU" to "Speak with a General Australian accent — clear and natural, not broad.",
    "en-US" to "Speak with a General American accent.",
    "en-CA" to "Speak with a Canadian English accent.",
    // Language-only fallback for Standard German (de-DE) and any de-* variant
    // not explicitly enumerated. Austrian (de-AT) and Swiss German (de-CH) have
    // their own entries below so the audio sounds like those regions rather than
    // the Hochdeutsch that the language-only fallback would give them.
    "de" to "Sprich auf Deutsch in einem klaren, hochdeutschen Akzent.",
    "de-AT" to "Sprich auf Deutsch mit einem klaren österreichischen Akzent.",
    "de-CH" to "Sprich auf Deutsch mit einem klaren deutschschweizerischen Akzent.",
    "fr-FR" to "Parle en français de France avec un accent parisien clair.",
    "fr-CA" to "Parle en français québécois avec un accent canadien-français.",
    "it" to "Parla in italiano con un accento italiano chiaro e naturale.",
    "es-ES" to "Habla en español de España con un acento castellano claro.",
    "es-MX" to "Habla en español mexicano con un acento neutro y claro.",
    "ca" to "Parla en català amb una pronunciació clara i natural.",
    "ru" to "Говори по-русски с чётким литературным произношением.",
    "pl" to "Mów po polsku z wyraźną i naturalną wymową.",
    "hr" to "Govori na hrvatskom jeziku s jasnim i prirodnim izgovorom.",
    "sl" to "Govori v slovenščini z jasnim in naravnim izgovorom.",
    "sr" to "Govori srpskim jezikom s jasnim i prirodnim izgovorom.",
    "bg" to "Говори на български с ясно и естествено произношение.",
    "cs" to "Mluvte česky s jasnou a přirozenou výslovností.",
    "sk" to "Hovorte po slovensky s jasnou a prirodzenou výslovnosťou.",
    "hu" to "Beszélj magyarul tiszta és természetes kiejtéssel.",
    "ro" to "Vorbiți în română cu o pronunție clară și naturală.",
    "el" to "Μίλα στα ελληνικά με καθαρή και φυσική προφορά.",
    "uk" to "Говори українською мовою з чітким літературним вимовленням.",
    "pt-BR" to "Fale em português brasileiro com sotaque neutro e claro.",
    "pt-PT" to "Fale em português europeu com um sotaque de Portugal claro e natural.",
    // Language-only fallback covers any pt-* variant not explicitly listed.
    "pt" to "Fale em português com pronúncia clara e natural.",
    "nl" to "Spreek in het Nederlands met een duidelijk en natuurlijk accent.",
    "sv" to "Tala på svenska med ett tydligt och naturligt uttal.",
    "da" to "Tal på dansk med en tydelig og naturlig udtale.",
    "nb" to "Snakk på norsk bokmål med tydelig og naturlig uttale.",
    "fi" to "Puhu suomea selkeällä ja luonnollisella ääntämyksellä.",
    "et" to "Räägi eesti keelt selge ja loomuliku hääldusega.",
    "lv" to "Runā latviski ar skaidru un dabīgu izrunu.",
    "lt" to "Kalbėk lietuviškai aiškiai ir natūraliai tariant.",
    "tr" to "Türkçe konuş, net ve doğal bir Türkçe aksanıyla.",
    "en-ZA" to "Speak with a South African English accent.",
    "id" to "Bicara dalam bahasa Indonesia dengan aksen yang jelas dan alami.",
    "ms" to "Bertutur dalam bahasa Melayu dengan sebutan yang jelas dan semula jadi.",
    "fil" to "Magsalita sa Filipino na may malinaw at natural na diin.",
    "sw" to "Soma maandishi yafuatayo kwa Kiswahili fasaha na matamshi wazi na ya asili.",
    "vi" to "Nói tiếng Việt với giọng điệu rõ ràng và tự nhiên.",
    "th" to "กรุณาอ่านเป็นภาษาไทยด้วยน้ำเสียงที่ชัดเจนและเป็นธรรมชาติ",
    "zh-CN" to "请用标准普通话朗读，发音清晰自然。",
    "zh-TW" to "請用標準國語朗讀，發音清晰自然。",
    // Language-only fallback covers generic zh locale and any zh-* variant
    // not explicitly listed (zh-HK, etc.) — all are Mandarin-readable.
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
 * Default voice is [DEFAULT_GEMINI_TTS_VOICE]. Other prebuilt voices are listed
 * in Google's docs; pass via [voiceName] when calling.
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
        style: TtsStyle = TtsStyle.WEATHER_FORECASTER,
        customStyleDirective: String = "",
    ): PcmAudio {
        val key = keyProvider.get().also {
            if (it.isBlank()) throw MissingApiKeyException()
        }

        val accent = locale?.let { geminiAccentDirectiveFor(it) }
        val prompt = buildString {
            append(styleDirectiveFor(style, customStyleDirective))
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
        // empty-response exception. Pull the body on a non-success status and
        // surface it.
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
