package app.clothescast.core.data.tts

import app.clothescast.core.data.insight.KeyProvider
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

    private companion object {
        // Four bytes of fake PCM are enough to exercise the success path; the
        // sample-rate is hard-coded in the client so we don't need to
        // round-trip it through the response.
        val SUCCESS_PCM_BYTES = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
    }
}
