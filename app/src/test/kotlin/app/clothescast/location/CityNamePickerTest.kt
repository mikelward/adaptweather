package app.clothescast.location

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [pickCityName]. Pure JVM — exercises the picker logic
 * with the same field shapes Android's `Geocoder` returns, without needing
 * Robolectric or an actual `Address`.
 */
class CityNamePickerTest {

    @Test
    fun `UK address with empty locality returns post town from addressLine`() {
        // Muswell Hill (London N10): Geocoder leaves locality blank; we
        // recover "London" from the addressLine token containing the postcode.
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = "Greater London",
            countryCode = "GB",
            postalCode = "N10 1XX",
            addressLines = listOf("1 Some Street, Muswell Hill, London N10 1XX, UK"),
        ) shouldBe "London"
    }

    @Test
    fun `UK address prefers post town over hamlet locality`() {
        // Oakley Green (Windsor SL4): Geocoder fills locality with the hamlet,
        // but the post town "Windsor" is what people expect.
        pickCityName(
            locality = "Oakley Green",
            subLocality = null,
            subAdminArea = "Windsor and Maidenhead",
            countryCode = "GB",
            postalCode = "SL4 5XX",
            addressLines = listOf("1 Some Lane, Oakley Green, Windsor SL4 5XX, UK"),
        ) shouldBe "Windsor"
    }

    @Test
    fun `US address falls through to locality`() {
        // Brooklyn 11201: GB branch is skipped; locality is the right answer.
        pickCityName(
            locality = "Brooklyn",
            subLocality = null,
            subAdminArea = "Kings County",
            countryCode = "US",
            postalCode = "11201",
            addressLines = listOf("123 Main St, Brooklyn, NY 11201, USA"),
        ) shouldBe "Brooklyn"
    }

    @Test
    fun `US locality is preferred even when postcode appears in addressLine`() {
        // Confirms the GB extraction logic doesn't accidentally fire for non-GB.
        pickCityName(
            locality = "New York",
            subLocality = null,
            subAdminArea = "New York County",
            countryCode = "US",
            postalCode = "10020",
            addressLines = listOf("10 W 50th St, New York, NY 10020, USA"),
        ) shouldBe "New York"
    }

    @Test
    fun `country code is matched case-insensitively`() {
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = "Greater London",
            countryCode = "gb",
            postalCode = "N10 1XX",
            addressLines = listOf("1 Some Street, Muswell Hill, London N10 1XX, UK"),
        ) shouldBe "London"
    }

    @Test
    fun `UK postcode without space in addressLine still matches`() {
        // postalCode field is "N10 1XX" but the addressLine packs it as "N101XX".
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = "Greater London",
            countryCode = "GB",
            postalCode = "N10 1XX",
            addressLines = listOf("1 Some Street, Muswell Hill, London N101XX, UK"),
        ) shouldBe "London"
    }

    @Test
    fun `UK address with no postcode falls back to locality chain`() {
        pickCityName(
            locality = "Edinburgh",
            subLocality = null,
            subAdminArea = "City of Edinburgh",
            countryCode = "GB",
            postalCode = null,
            addressLines = listOf("Some Street, Edinburgh, UK"),
        ) shouldBe "Edinburgh"
    }

    @Test
    fun `UK address whose addressLines do not contain the postcode falls back`() {
        pickCityName(
            locality = "Edinburgh",
            subLocality = null,
            subAdminArea = "City of Edinburgh",
            countryCode = "GB",
            postalCode = "EH1 1AA",
            addressLines = listOf("Some Street, no postcode here, UK"),
        ) shouldBe "Edinburgh"
    }

    @Test
    fun `UK addressLine token that is only the postcode is skipped`() {
        // Stripping the postcode leaves the token empty; don't return blank.
        pickCityName(
            locality = "Edinburgh",
            subLocality = null,
            subAdminArea = "City of Edinburgh",
            countryCode = "GB",
            postalCode = "EH1 1AA",
            addressLines = listOf("Some Street, EH1 1AA, UK"),
        ) shouldBe "Edinburgh"
    }

    @Test
    fun `UK with empty locality and no addressLine match falls back to subAdminArea`() {
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = "Greater London",
            countryCode = "GB",
            postalCode = "N10 1XX",
            addressLines = listOf("Some Street, no postcode here, UK"),
        ) shouldBe "Greater London"
    }

    @Test
    fun `multi-line addressLines are scanned`() {
        // Some Geocoder responses split across multiple addressLines.
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = "Greater London",
            countryCode = "GB",
            postalCode = "N10 1XX",
            addressLines = listOf("1 Some Street", "Muswell Hill, London N10 1XX", "UK"),
        ) shouldBe "London"
    }

    @Test
    fun `everything blank returns null`() {
        pickCityName(
            locality = null,
            subLocality = null,
            subAdminArea = null,
            countryCode = "GB",
            postalCode = null,
            addressLines = emptyList(),
        ) shouldBe null
    }

    @Test
    fun `locality is trimmed`() {
        pickCityName(
            locality = "  Boston  ",
            subLocality = null,
            subAdminArea = null,
            countryCode = "US",
            postalCode = "02108",
            addressLines = listOf("1 Beacon St, Boston, MA 02108, USA"),
        ) shouldBe "Boston"
    }
}
