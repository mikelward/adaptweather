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

class OpenAITtsClientTest {

    private fun mockClient(
        responseBody: ByteArray = SUCCESS_PCM_BYTES,
        status: HttpStatusCode = HttpStatusCode.OK,
        captureRequest: (HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            captureRequest(request)
            respond(
                content = ByteReadChannel(responseBody),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "audio/pcm"),
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

    private fun capturedBodyOf(request: HttpRequestData): String =
        (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes()
            .toString(Charsets.UTF_8)

    @Test
    fun `posts to the speech endpoint with the bearer token`() = runTest {
        var captured: HttpRequestData? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val req = checkNotNull(captured)
        req.url.toString() shouldBe "https://api.openai.com/v1/audio/speech"
        req.headers["Authorization"] shouldBe "Bearer test-key"
    }

    @Test
    fun `default model is gpt-4o-mini-tts`() = runTest {
        // We bumped the default off `tts-1` because gpt-4o-mini-tts is the only
        // OpenAI TTS model that honours the `instructions` field — without that
        // the variant picker stays a no-op for OpenAI. Lock the default in a
        // test so a careless edit can't silently regress accent steering.
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"model\":\"gpt-4o-mini-tts\"")
    }

    @Test
    fun `request body includes british instructions for en-GB locale`() = runTest {
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.UK)

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"instructions\":\"Speak with a British English accent.\"")
    }

    @Test
    fun `request body includes australian instructions for en-AU locale`() = runTest {
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.forLanguageTag("en-AU"))

        val body = checkNotNull(capturedBody)
        body.shouldContain("Australian English accent")
    }

    @Test
    fun `request body omits instructions when locale is null`() = runTest {
        // No locale → no instructions field on the wire (kotlinx defaults
        // `encodeDefaults = false`). Important because OpenAI's older TTS
        // models reject unknown / unsupported fields with a 400.
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldNotContain("instructions")
    }

    @Test
    fun `request body omits instructions for unknown english variants`() = runTest {
        // en-CA isn't in our supported variant list — fall through to the
        // model's default rather than picking a wrong-but-confident accent.
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", locale = Locale.forLanguageTag("en-CA"))

        val body = checkNotNull(capturedBody)
        body.shouldNotContain("instructions")
    }

    @Test
    fun `throws MissingApiKeyException when key is blank`() = runTest {
        val client = OpenAITtsClient(
            httpClient = mockClient(),
            keyProvider = FakeKeyProvider("   "),
        )

        shouldThrow<MissingApiKeyException> { client.synthesize(text = "hello") }
    }

    @Test
    fun `throws OpenAITtsHttpException with parsed error message on 400`() = runTest {
        val client = OpenAITtsClient(
            httpClient = mockClient(
                status = HttpStatusCode.BadRequest,
                responseBody = """{"error":{"message":"Invalid voice 'nope'."}}"""
                    .toByteArray(Charsets.UTF_8),
            ),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val ex = shouldThrow<OpenAITtsHttpException> { client.synthesize(text = "hi") }
        ex.message shouldBe "OpenAI TTS HTTP 400: Invalid voice 'nope'."
    }

    private companion object {
        // Four bytes of fake PCM are enough to exercise the success path; the
        // sample-rate header is hard-coded in the client so we don't need to
        // round-trip it through the response.
        val SUCCESS_PCM_BYTES = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
    }
}
