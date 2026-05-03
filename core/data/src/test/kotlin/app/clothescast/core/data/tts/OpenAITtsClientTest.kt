package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
        // We bumped the default off `tts-1` because gpt-4o-mini-tts is a
        // documented quality upgrade (positioned by OpenAI as comparable to
        // tts-1-hd). Lock the default in a test so a careless edit doesn't
        // silently regress users to the older model.
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
    fun `request body sends a pace and clarity instruction`() = runTest {
        // `instructions` doesn't steer accent on gpt-4o-mini-tts (the voice's
        // baked-in timbre dominates — that part is voice-bound, controlled by
        // the picker filter), but it *does* steer pace and enunciation. We
        // send a constant directive aimed at the "too fast / dropped letters"
        // complaints from field reports. Lock this in so the directive
        // doesn't silently disappear from a careless edit.
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"instructions\":")
        body.shouldContain("friendly morning radio host or public address system")
    }

    @Test
    fun `synthesize forwards a per-call speed override`() = runTest {
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", speed = 0.85)

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"speed\":0.85")
    }

    @Test
    fun `synthesize omits the speed field at the API default`() = runTest {
        // The default 1.0 is the API's stock pace; we leave the field off
        // entirely rather than send a redundant value, both to keep the
        // request body minimal and to avoid the (slim) chance the model
        // changes behaviour at exactly 1.0.
        var capturedBody: String? = null
        val client = OpenAITtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        check(!body.contains("\"speed\"")) { "expected no speed field at default; body was: $body" }
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
