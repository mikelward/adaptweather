package app.adaptweather.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class WeatherAlertTest {
    private val window = Instant.parse("2026-04-25T15:00:00Z") to Instant.parse("2026-04-25T20:00:00Z")

    private fun alert(severity: AlertSeverity) = WeatherAlert(
        event = "Test",
        severity = severity,
        headline = null,
        description = null,
        onset = window.first,
        expires = window.second,
    )

    @Test
    fun `severe and extreme are high priority`() {
        alert(AlertSeverity.SEVERE).isHighPriority() shouldBe true
        alert(AlertSeverity.EXTREME).isHighPriority() shouldBe true
    }

    @Test
    fun `minor moderate and unknown are not high priority`() {
        alert(AlertSeverity.MINOR).isHighPriority() shouldBe false
        alert(AlertSeverity.MODERATE).isHighPriority() shouldBe false
        alert(AlertSeverity.UNKNOWN).isHighPriority() shouldBe false
    }
}
