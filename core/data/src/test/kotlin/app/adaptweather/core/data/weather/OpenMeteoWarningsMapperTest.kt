package app.adaptweather.core.data.weather

import app.adaptweather.core.domain.model.AlertSeverity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

class OpenMeteoWarningsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun loadFixture(): OpenMeteoWarningsResponse {
        val text = checkNotNull(javaClass.getResourceAsStream("/openmeteo_warnings_london.json")) {
            "fixture missing"
        }.bufferedReader().readText()
        return json.decodeFromString(text)
    }

    @Test
    fun `maps event severity headline and valid window`() {
        val alerts = OpenMeteoWarningsMapper.toAlerts(loadFixture())

        alerts shouldHaveSize 3
        val severe = alerts[0]
        severe.event shouldBe "Severe Thunderstorm Warning"
        severe.severity shouldBe AlertSeverity.SEVERE
        severe.headline shouldBe "Damaging hail and 90 km/h gusts expected"
        severe.onset shouldBe Instant.parse("2026-04-25T15:00:00Z")
        severe.expires shouldBe Instant.parse("2026-04-25T20:00:00Z")
    }

    @Test
    fun `severity is parsed case-insensitively`() {
        val response = OpenMeteoWarningsResponse(
            warnings = listOf(
                WarningDto("a", "minor", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("b", "MODERATE", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("c", "Severe", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("d", "extreme", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
            ),
        )

        val alerts = OpenMeteoWarningsMapper.toAlerts(response)

        alerts.map { it.severity } shouldBe listOf(
            AlertSeverity.MINOR,
            AlertSeverity.MODERATE,
            AlertSeverity.SEVERE,
            AlertSeverity.EXTREME,
        )
    }

    @Test
    fun `unknown or null severity falls back to UNKNOWN`() {
        val response = OpenMeteoWarningsResponse(
            warnings = listOf(
                WarningDto("a", null, null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("b", "weird-grade", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
            ),
        )

        val alerts = OpenMeteoWarningsMapper.toAlerts(response)

        alerts.map { it.severity } shouldBe listOf(AlertSeverity.UNKNOWN, AlertSeverity.UNKNOWN)
    }

    @Test
    fun `unparseable timestamps drop only the offending alert`() {
        val response = OpenMeteoWarningsResponse(
            warnings = listOf(
                WarningDto("good", "Severe", null, null, "2026-04-25T15:00:00Z", "2026-04-25T20:00:00Z"),
                WarningDto("bad-onset", "Severe", null, null, "not-a-date", "2026-04-25T20:00:00Z"),
                WarningDto("bad-expires", "Severe", null, null, "2026-04-25T15:00:00Z", "also-not-a-date"),
            ),
        )

        val alerts = OpenMeteoWarningsMapper.toAlerts(response)

        alerts shouldHaveSize 1
        alerts.single().event shouldBe "good"
    }

    @Test
    fun `empty warnings list yields empty alerts list`() {
        val alerts = OpenMeteoWarningsMapper.toAlerts(OpenMeteoWarningsResponse(emptyList()))
        alerts shouldBe emptyList()
    }

    @Test
    fun `severity parsing is locale-invariant under Turkish default locale`() {
        // tr-TR's lowercase mapping turns "MINOR" into "mınor" (dotless ı). Under
        // a locale-sensitive case fold this would fall through to UNKNOWN.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val response = OpenMeteoWarningsResponse(
            warnings = listOf(
                WarningDto("a", "MINOR", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("b", "MODERATE", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("c", "SEVERE", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
                WarningDto("d", "EXTREME", null, null, "2026-04-25T00:00:00Z", "2026-04-25T01:00:00Z"),
            ),
        )

        val alerts = OpenMeteoWarningsMapper.toAlerts(response)

        alerts.map { it.severity } shouldBe listOf(
            AlertSeverity.MINOR,
            AlertSeverity.MODERATE,
            AlertSeverity.SEVERE,
            AlertSeverity.EXTREME,
        )
    }
}
