package app.clothescast.ui.today

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [shortLocationLabel] — the helper that trims the
 * forward-geocoded "Boston, Massachusetts, United States" displayName down to
 * just the city for the Today header. Pure JVM, no Compose or Android deps.
 */
class ShortLocationLabelTest {

    @Test
    fun `null input returns null`() {
        shortLocationLabel(null) shouldBe null
    }

    @Test
    fun `blank input returns null`() {
        shortLocationLabel("   ") shouldBe null
    }

    @Test
    fun `LocationResolver placeholder is treated as no name`() {
        // "Device location" is the placeholder LocationResolver stamps when
        // the resolver couldn't (or didn't try to) fetch a real city — the
        // home screen should fall back to date-only rather than render that
        // string verbatim.
        shortLocationLabel("Device location") shouldBe null
    }

    @Test
    fun `bare city name passes through unchanged`() {
        shortLocationLabel("Boston") shouldBe "Boston"
    }

    @Test
    fun `forward-geocoded triple drops admin and country`() {
        shortLocationLabel("Boston, Massachusetts, United States") shouldBe "Boston"
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        shortLocationLabel("  London, UK  ") shouldBe "London"
    }

    @Test
    fun `unicode city names round-trip`() {
        shortLocationLabel("São Paulo, Brazil") shouldBe "São Paulo"
    }
}
