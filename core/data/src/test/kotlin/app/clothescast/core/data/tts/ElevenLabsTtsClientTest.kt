package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.insight.MissingApiKeyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ElevenLabsTtsClientTest {

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
    fun `posts to the speech endpoint with the xi-api-key header`() = runTest {
        var captured: HttpRequestData? = null
        val client = ElevenLabsTtsClient(
            httpClient = mockClient { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val req = checkNotNull(captured)
        req.url.host shouldBe "api.elevenlabs.io"
        req.headers["xi-api-key"] shouldBe "test-key"
    }

    @Test
    fun `default model is eleven_turbo_v2_5`() = runTest {
        // Pin the default so a careless edit doesn't silently regress users
        // back to multilingual_v2 (the previous default before turbo's
        // pricing / latency / pacing wins).
        var capturedBody: String? = null
        val client = ElevenLabsTtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"model_id\":\"eleven_turbo_v2_5\"")
    }

    @Test
    fun `synthesize forwards a per-call model override into the request body`() = runTest {
        // The ViewModel passes the user-picked model on every call so the
        // client constructor's default doesn't shadow Settings.
        var capturedBody: String? = null
        val client = ElevenLabsTtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", model = ELEVENLABS_MODEL_FLASH_V2_5)

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"model_id\":\"eleven_flash_v2_5\"")
    }

    @Test
    fun `synthesize forwards a per-call speed override into voice_settings`() = runTest {
        var capturedBody: String? = null
        val client = ElevenLabsTtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello", speed = 1.05)

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"speed\":1.05")
    }

    @Test
    fun `request body sends voice_settings with speed and stability`() = runTest {
        // We override per-voice library defaults on every clip so the pacing
        // / stability tuning is consistent regardless of which voice the user
        // picked. Lock the values in so a careless edit doesn't silently
        // revert to whatever the voice author tuned.
        var capturedBody: String? = null
        val client = ElevenLabsTtsClient(
            httpClient = mockClient { capturedBody = capturedBodyOf(it) },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.synthesize(text = "hello")

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"voice_settings\":")
        body.shouldContain("\"speed\":0.9")
        body.shouldContain("\"stability\":0.65")
    }

    @Test
    fun `listVoices GETs v1 voices and parses the wire envelope`() = runTest {
        var captured: HttpRequestData? = null
        val client = ElevenLabsTtsClient(
            httpClient = jsonMockClient(VOICES_RESPONSE_BODY) { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
        )

        val voices = client.listVoices()

        val req = checkNotNull(captured)
        req.method shouldBe HttpMethod.Get
        req.url.host shouldBe "api.elevenlabs.io"
        req.url.encodedPath shouldBe "/v1/voices"
        req.headers["xi-api-key"] shouldBe "test-key"
        voices shouldHaveSize 2
        voices[0].id shouldBe "EXAVITQu4vr4xnSDxMaL"
        voices[0].name shouldBe "Sarah"
        voices[0].accent shouldBe "american"
        voices[0].description shouldBe "warm"
        // Cloned voice with no labels at all — accent and description fall through to null.
        voices[1].id shouldBe "custom-clone-id"
        voices[1].name shouldBe "My Clone"
        voices[1].accent shouldBe null
        voices[1].description shouldBe null
    }

    @Test
    fun `listVoices throws MissingApiKeyException when key is blank`() = runTest {
        val client = ElevenLabsTtsClient(
            httpClient = jsonMockClient("""{"voices":[]}"""),
            keyProvider = FakeKeyProvider("   "),
        )
        shouldThrow<MissingApiKeyException> { client.listVoices() }
    }

    @Test
    fun `listVoices surfaces HTTP failure as ElevenLabsTtsHttpException`() = runTest {
        val client = ElevenLabsTtsClient(
            httpClient = jsonMockClient(
                """{"detail":{"status":"unauthorized","message":"Invalid API key"}}""",
                status = HttpStatusCode.Unauthorized,
            ),
            keyProvider = FakeKeyProvider("bad-key"),
        )
        val ex = shouldThrow<ElevenLabsTtsHttpException> { client.listVoices() }
        ex.status shouldBe HttpStatusCode.Unauthorized
        checkNotNull(ex.message).shouldContain("Invalid API key")
    }

    private fun jsonMockClient(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        captureRequest: (HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            captureRequest(request)
            respond(
                content = ByteReadChannel(body),
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

    private companion object {
        // Four bytes of fake PCM are enough to exercise the success path; the
        // sample-rate is hard-coded in the client so we don't need to
        // round-trip it through the response.
        val SUCCESS_PCM_BYTES = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        // Two-voice fixture: one premade with full labels (Sarah) and one
        // user-cloned voice with no labels block at all — exercises both
        // mapping branches in `listVoices`. Extra fields (`samples`,
        // `fine_tuning`, …) are dropped because of `ignoreUnknownKeys`.
        val VOICES_RESPONSE_BODY = """
            {
              "voices": [
                {
                  "voice_id": "EXAVITQu4vr4xnSDxMaL",
                  "name": "Sarah",
                  "category": "premade",
                  "labels": {
                    "accent": "american",
                    "description": "warm",
                    "age": "young",
                    "gender": "female",
                    "use_case": "narration"
                  },
                  "samples": null
                },
                {
                  "voice_id": "custom-clone-id",
                  "name": "My Clone",
                  "category": "cloned"
                }
              ]
            }
        """.trimIndent()
    }
}
