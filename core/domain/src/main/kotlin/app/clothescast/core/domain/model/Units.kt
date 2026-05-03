package app.clothescast.core.domain.model

fun Double.toUnit(unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.CELSIUS -> this
    TemperatureUnit.FAHRENHEIT -> this * 9.0 / 5.0 + 32.0
}

/** Inverse of [toUnit]: interprets the receiver as already being in [unit] and converts to °C. */
fun Double.fromUnit(unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.CELSIUS -> this
    TemperatureUnit.FAHRENHEIT -> (this - 32.0) * 5.0 / 9.0
}

fun TemperatureUnit.symbol(): String = when (this) {
    TemperatureUnit.CELSIUS -> "°C"
    TemperatureUnit.FAHRENHEIT -> "°F"
}
