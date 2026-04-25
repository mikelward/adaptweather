package com.adaptweather.core.data.weather

import com.adaptweather.core.domain.model.Location
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
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

class OpenMeteoClientTest {
    private val london = Location(latitude = 51.5074, longitude = -0.1278, displayName = "London")

    private fun fixtureBytes(): ByteReadChannel {
        val text = checkNotNull(javaClass.getResourceAsStream("/openmeteo_london.json")) {
            "fixture missing"
        }.bufferedReader().readText()
        return ByteReadChannel(text)
    }

    private fun mockClient(captureRequest: (HttpRequestData) -> Unit): HttpClient {
        val engine = MockEngine { request ->
            captureRequest(request)
            respond(
                content = fixtureBytes(),
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

    @Test
    fun `request hits open-meteo with required parameters`() = runTest {
        var captured: HttpRequestData? = null
        val client = OpenMeteoClient(mockClient { captured = it })

        client.fetchForecast(london)

        val req = checkNotNull(captured)
        req.url.host shouldBe OPEN_METEO_HOST
        req.url.encodedPath shouldBe "/v1/forecast"

        val params = req.url.parameters
        params["latitude"] shouldBe "51.5074"
        params["longitude"] shouldBe "-0.1278"
        params["past_days"] shouldBe "1"
        params["forecast_days"] shouldBe "1"
        params["timezone"] shouldBe "auto"

        val daily = checkNotNull(params["daily"]).split(",")
        daily.shouldContainAll(
            listOf(
                "temperature_2m_min",
                "temperature_2m_max",
                "precipitation_probability_max",
                "precipitation_sum",
                "weather_code",
            ),
        )

        val hourly = checkNotNull(params["hourly"]).split(",")
        hourly.shouldContain("temperature_2m")
        hourly.shouldContain("precipitation_probability")
        hourly.shouldContain("weather_code")
    }

    @Test
    fun `parses fixture into a forecast bundle`() = runTest {
        val client = OpenMeteoClient(mockClient { })

        val bundle = client.fetchForecast(london)

        bundle.yesterday.temperatureMaxC shouldBe 18.0
        bundle.today.temperatureMaxC shouldBe 24.0
        bundle.today.hourly.size shouldBe 8
    }
}
