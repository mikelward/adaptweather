package app.clothescast.core.data.weather

import app.clothescast.core.domain.model.WeatherCondition

/**
 * WMO weather interpretation code → coarse [WeatherCondition] bucket.
 * Reference: https://open-meteo.com/en/docs (search "WMO Weather interpretation codes").
 */
internal object WmoCodeMapper {
    fun map(code: Int?): WeatherCondition = when (code) {
        null -> WeatherCondition.UNKNOWN
        0, 1 -> WeatherCondition.CLEAR
        2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }
}
