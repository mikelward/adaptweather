package app.clothescast.location

/** Picks a user-facing city name from the structured fields of an Android `Address`. */
internal fun pickCityName(
    locality: String?,
    subLocality: String?,
    subAdminArea: String?,
    countryCode: String?,
    postalCode: String?,
    addressLines: List<String>,
): String? {
    // UK: Google's Geocoder fills `locality` with the hamlet (Oakley Green) or leaves it blank
    // for inner-London residentials. The Royal Mail post town — what people actually call the
    // place — is the addressLine token containing the postcode.
    if (countryCode.equals("GB", ignoreCase = true)) {
        extractUkPostTown(postalCode, addressLines)?.let { return it }
    }
    return listOf(locality, subLocality, subAdminArea)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

private fun extractUkPostTown(postalCode: String?, addressLines: List<String>): String? {
    if (postalCode.isNullOrBlank()) return null
    val pcPattern = postalCode.trim()
        .split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .joinToString("\\s*") { Regex.escape(it) }
        .toRegex(RegexOption.IGNORE_CASE)
    for (line in addressLines) {
        for (rawToken in line.split(",")) {
            val token = rawToken.trim()
            if (token.isEmpty() || !pcPattern.containsMatchIn(token)) continue
            val cleaned = pcPattern.replace(token, " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
                .trim(',')
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }
    }
    return null
}
