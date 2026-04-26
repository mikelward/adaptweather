package app.adaptweather.core.data.insight

import app.adaptweather.core.domain.usecase.Prompt
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

class DirectGeminiClientTest {
    private val prompt = Prompt(
        systemInstruction = "Write one short sentence.",
        userMessage = "Yesterday: cool. Today: warm. Compare and advise.",
    )

    private fun mockClient(
        responseBody: String,
        captureRequest: (HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            captureRequest(request)
            respond(
                content = ByteReadChannel(responseBody),
                status = HttpStatusCode.OK,
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
    fun `posts to expected gemini endpoint with key header and json body`() = runTest {
        var captured: HttpRequestData? = null
        val client = DirectGeminiClient(
            httpClient = mockClient(SUCCESS_BODY) { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.generate(prompt)

        val req = checkNotNull(captured)
        req.url.host shouldBe GEMINI_HOST
        req.url.encodedPath shouldBe "/v1beta/models/gemini-2.5-flash:generateContent"
        req.headers["x-goog-api-key"] shouldBe "test-key"
        req.body.contentType.toString().shouldContain("application/json")
    }

    @Test
    fun `request body carries system instruction, user message, and generation config`() = runTest {
        var capturedBody: String? = null
        val client = DirectGeminiClient(
            httpClient = mockClient(SUCCESS_BODY) {
                capturedBody = (it.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                    .bytes()
                    .toString(Charsets.UTF_8)
            },
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.generate(prompt)

        val body = checkNotNull(capturedBody)
        body.shouldContain("\"systemInstruction\"")
        body.shouldContain("Write one short sentence.")
        body.shouldContain("Yesterday: cool. Today: warm. Compare and advise.")
        body.shouldContain("\"role\":\"user\"")
        body.shouldContain("\"temperature\":0.4")
        body.shouldContain("\"maxOutputTokens\":100")
    }

    @Test
    fun `extracts the text from the first candidate`() = runTest {
        val client = DirectGeminiClient(
            httpClient = mockClient(SUCCESS_BODY),
            keyProvider = FakeKeyProvider("test-key"),
        )

        client.generate(prompt) shouldBe "Warmer than yesterday — go without a jumper."
    }

    @Test
    fun `throws MissingApiKeyException when key is blank`() = runTest {
        val client = DirectGeminiClient(
            httpClient = mockClient(SUCCESS_BODY),
            keyProvider = FakeKeyProvider("   "),
        )

        shouldThrow<MissingApiKeyException> { client.generate(prompt) }
    }

    @Test
    fun `throws GeminiEmptyResponseException when no candidates returned`() = runTest {
        val client = DirectGeminiClient(
            httpClient = mockClient("""{"candidates":[]}"""),
            keyProvider = FakeKeyProvider("test-key"),
        )

        shouldThrow<GeminiEmptyResponseException> { client.generate(prompt) }
    }

    @Test
    fun `throws GeminiBlockedException when promptFeedback indicates a block`() = runTest {
        val client = DirectGeminiClient(
            httpClient = mockClient("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
            keyProvider = FakeKeyProvider("test-key"),
        )

        val ex = shouldThrow<GeminiBlockedException> { client.generate(prompt) }
        ex.message!!.shouldContain("SAFETY")
    }

    @Test
    fun `honors model override`() = runTest {
        var captured: HttpRequestData? = null
        val client = DirectGeminiClient(
            httpClient = mockClient(SUCCESS_BODY) { captured = it },
            keyProvider = FakeKeyProvider("test-key"),
            model = "gemini-2.5-pro",
        )

        client.generate(prompt)

        captured!!.url.encodedPath shouldBe "/v1beta/models/gemini-2.5-pro:generateContent"
    }

    private companion object {
        const val SUCCESS_BODY = """
            {
              "candidates": [
                {
                  "content": {
                    "role": "model",
                    "parts": [
                      {"text": "Warmer than yesterday — go without a jumper."}
                    ]
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """
    }
}
