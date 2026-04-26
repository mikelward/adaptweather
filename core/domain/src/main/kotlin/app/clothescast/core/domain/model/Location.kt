package app.clothescast.core.domain.model

data class Location(
    val latitude: Double,
    val longitude: Double,
    val displayName: String? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}
