package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.Locale

class GeminiTtsClientTest {

    private fun mockClient(
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        captureRequest: (HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            captureRequest(request)
            respond(
                content = ByteReadChannel(responseBody),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private class FakeKeyProvider(private val key: String) : KeyProvider {
        override suspend fun get() = key
    }

    @Test
    fun `posts to the default tts model endpoint with the api key header`() = runTest {
        var captured: HttpRequestData? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val req = checkNotNull(captured)
        req.url.host shouldBe GEMINI_HOST
        req.url.encodedPath shouldBe "/v1beta/models/gemini-2.5-flash-preview-tts:generateContent"
        req.headers["x-goog-api-key"] shouldBe "test-key"
    }

    @Test
    fun `request body includes responseModalities AUDIO`() = runTest {
        // Regression for the kotlinx.serialization `encodeDefaults = false` trap:
        // when the runtime value equals the declared default, the field is dropped
        // and Gemini falls back to its TEXT modality default — which a TTS model
        // can't satisfy ("The requested combination of response modalities (TEXT)
        // is not supported by the model.").
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"responseModalities\":[\"AUDIO\"]")
    }

    @Test
    fun `request body prepends the studio-voice style directive to the user text`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello world")

        val body = checkNotNull(capturedBody)
        body.shouldContain("clean, crisp studio voice")
        body.shouldContain("hello world")
    }

    @Test
    fun `request body includes a british accent directive for en-GB locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.UK)

        val body = checkNotNull(capturedBody)
        body.shouldContain("Standard Southern British accent")
    }

    @Test
    fun `request body includes an australian accent directive for en-AU locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.forLanguageTag("en-AU"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("General Australian accent")
    }

    @Test
    fun `request body includes a german directive for de locale`() = runTest {
        // German is the first non-English entry in the directive table — verifies
        // the language-only fallback (no language-COUNTRY entry) actually fires
        // and that the model is told to read the prose as German rather than
        // the en-default North American English.
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hallo", locale = Locale.forLanguageTag("de-DE"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("Sprich auf Deutsch")
        // de-DE gets the Hochdeutsch fallback, not an Austrian or Swiss directive
        body.shouldNotContain("österreichischen")
        body.shouldNotContain("deutschschweizerischen")
    }

    @Test
    fun `request body includes an austrian accent directive for de-AT locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hallo", locale = Locale.forLanguageTag("de-AT"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("österreichischen")
        body.shouldNotContain("hochdeutschen")
    }

    @Test
    fun `request body includes a swiss german accent directive for de-CH locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hallo", locale = Locale.forLanguageTag("de-CH"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("deutschschweizerischen")
        body.shouldNotContain("hochdeutschen")
    }

    @Test
    fun `request body includes a european portuguese directive for pt-PT locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "olá", locale = Locale.forLanguageTag("pt-PT"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("português europeu")
        // Must not pick up the Brazilian directive
        body.shouldNotContain("brasileiro")
    }

    @Test
    fun `request body includes a taiwanese mandarin directive for zh-TW locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "你好", locale = Locale.forLanguageTag("zh-TW"))

        val body = checkNotNull(capturedBody)
        // zh-TW uses the Traditional Chinese "國語" (guóyǔ) directive,
        // not the Simplified Chinese "普通话" (pǔtōnghuà) fallback.
        body.shouldContain("國語")
        body.shouldNotContain("普通话")
    }

    @Test
    fun `request body picks the saudi arabic directive for ar-SA locale`() = runTest {
        // PR #218 split the previously-language-only `ar` directive into four
        // country-specific entries so the picker variants we offer in
        // Settings actually steer Gemini, not just label the picker. Pin on
        // the country-distinct "بنطق سعودي" (Saudi pronunciation) so a swap
        // to a different country's directive surfaces here.
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hi", locale = Locale.forLanguageTag("ar-SA"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("بنطق سعودي")
    }

    @Test
    fun `request body picks the egyptian arabic directive for ar-EG locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hi", locale = Locale.forLanguageTag("ar-EG"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("بنطق مصري")
    }

    @Test
    fun `request body picks the emirati arabic directive for ar-AE locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hi", locale = Locale.forLanguageTag("ar-AE"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("بنطق إماراتي")
    }

    @Test
    fun `request body picks the moroccan arabic directive for ar-MA locale`() = runTest {
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hi", locale = Locale.forLanguageTag("ar-MA"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("بنطق مغربي")
    }

    @Test
    fun `request body falls back to the bare ar directive for unrecognised arabic variants`() = runTest {
        // ar-LB (Lebanon) isn't enumerated — fall through to the language-only
        // `ar` entry so the model still gets a "read this in Arabic" nudge
        // rather than no directive at all. Pinning on the bare `ar` directive
        // (no country word) verifies the language-only fallback path.
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hi", locale = Locale.forLanguageTag("ar-LB"))

        val body = checkNotNull(capturedBody)
        // Bare `ar` directive omits the country adjective.
        body.shouldContain("اقرأ النص التالي بالعربية بنطق")
        body.shouldNotContain("سعودي")
        body.shouldNotContain("مصري")
    }

    @Test
    fun `request body omits the accent directive for unknown english variants`() = runTest {
        // en-NZ isn't in our supported variant list — fall through to whatever the
        // model defaults to rather than picking a wrong-but-confident accent.
        var capturedBody: String? = null
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.forLanguageTag("en-NZ"))

        val body = checkNotNull(capturedBody)
        // No accent directive at all — none of the SSB / General Australian /
        // General American sentinels should leak in for unknown variants.
        body.shouldNotContain("Speak with a")
    }

    @Test
    fun `decodes inline pcm and parses sample rate from mime type`() = runTest {
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val audio = client.synthesize(text = "hello")

        // SUCCESS_BODY encodes the four bytes 0xDE 0xAD 0xBE 0xEF.
        audio.bytes.toList() shouldBe listOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        audio.sampleRate shouldBe 24_000
    }

    @Test
    fun `throws MissingApiKeyException when key is blank`() = runTest {
        val client = GeminiTtsClient(
            httpClient = mockClient(SUCCESS_BODY),
            keyProvider = FakeKeyProvider("   "),
        )

        shouldThrow<MissingApiKeyException> { client.synthesize(text = "hello") }
    }

    @Test
    fun `throws GeminiTtsHttpException with parsed error message on 400`() = runTest {
        val client = GeminiTtsClient(
            httpClient = mockClient(
                status = HttpStatusCode.BadRequest,
                responseBody = """
                    {"error":{"code":400,"message":"Invalid voice 'Nope'.","status":"INVALID_ARGUMENT"}}
                """.trimIndent(),
            ),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val ex = shouldThrow<GeminiTtsHttpException> { client.synthesize(text = "hi") }

        ex.status shouldBe HttpStatusCode.BadRequest
        ex.message shouldBe "Gemini TTS HTTP 400: Invalid voice 'Nope'."
        // Should NOT contain the raw JSON envelope keys — that's what made the old
        // toast unreadable.
        ex.message!!.shouldNotContain("INVALID_ARGUMENT")
        ex.message!!.shouldNotContain("\"error\"")
    }

    @Test
    fun `surfaces a placeholder when the error body is empty`() = runTest {
        val client = GeminiTtsClient(
            httpClient = mockClient(
                status = HttpStatusCode.BadGateway,
                responseBody = "",
            ),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val ex = shouldThrow<GeminiTtsHttpException> { client.synthesize(text = "hi") }

        ex.message shouldBe "Gemini TTS HTTP 502: (empty body)"
    }

    @Test
    fun `falls back to truncated raw body when error envelope is unparseable`() = runTest {
        val raw = "x".repeat(500)
        val client = GeminiTtsClient(
            httpClient = mockClient(
                status = HttpStatusCode.InternalServerError,
                responseBody = raw,
            ),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val ex = shouldThrow<GeminiTtsHttpException> { client.synthesize(text = "hi") }

        ex.message!!.shouldContain("Gemini TTS HTTP 500: ")
        // Truncated to the 160-char excerpt cap, not the full 500-char body.
        (ex.message!!.length < 250) shouldBe true
    }

    @Test
    fun `throws GeminiTtsEmptyResponseException when response has no inline audio`() = runTest {
        val client = GeminiTtsClient(
            httpClient = mockClient("""{"candidates":[]}"""),
            keyProvider = FakeKeyProvider("test-key"),
        )

        shouldThrow<GeminiTtsEmptyResponseException> { client.synthesize(text = "hi") }
    }

    private companion object {
        // 0xDE 0xAD 0xBE 0xEF base64-encoded.
        const val SUCCESS_BODY = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "inlineData": {
                      "mimeType": "audio/L16;codec=pcm;rate=24000",
                      "data": "3q2+7w=="
                    }
                  }]
                }
              }]
            }
        """
    }
}
