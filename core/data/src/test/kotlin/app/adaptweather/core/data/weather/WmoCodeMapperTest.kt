package app.adaptweather.core.data.weather

import app.adaptweather.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WmoCodeMapperTest {
    @ParameterizedTest
    @CsvSource(
        "0, CLEAR",
        "1, CLEAR",
        "2, PARTLY_CLOUDY",
        "3, CLOUDY",
        "45, FOG",
        "48, FOG",
        "51, DRIZZLE",
        "55, DRIZZLE",
        "57, DRIZZLE",
        "61, RAIN",
        "63, RAIN",
        "65, RAIN",
        "80, RAIN",
        "82, RAIN",
        "71, SNOW",
        "75, SNOW",
        "85, SNOW",
        "95, THUNDERSTORM",
        "96, THUNDERSTORM",
        "99, THUNDERSTORM",
        "100, UNKNOWN",
        "-1, UNKNOWN",
    )
    fun `maps known WMO codes`(code: Int, expected: WeatherCondition) {
        WmoCodeMapper.map(code) shouldBe expected
    }

    @Test
    fun `null code maps to UNKNOWN`() {
        WmoCodeMapper.map(null) shouldBe WeatherCondition.UNKNOWN
    }
}
