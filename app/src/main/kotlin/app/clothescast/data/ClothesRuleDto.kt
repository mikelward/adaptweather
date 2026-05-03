package app.clothescast.data

import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.TemperatureUnit
import kotlinx.serialization.Serializable

/**
 * On-disk representation of a [ClothesRule]. The domain type uses a sealed interface
 * for [ClothesRule.Condition], which is awkward to serialize directly — this DTO
 * flattens it to (type, value, unit) and round-trips through [toDomain] / [toDto].
 *
 * `type` strings are intentionally non-camelCase so they're stable identifiers in JSON
 * even if class names are renamed. [unit] is nullable so JSON written by app versions
 * before unit-aware thresholds existed still deserialises (legacy = always Celsius);
 * unit is meaningless for precipitation rules and stays null there.
 */
@Serializable
internal data class ClothesRuleDto(
    val item: String,
    val type: String,
    val value: Double,
    val unit: String? = null,
) {
    fun toDomain(): ClothesRule = ClothesRule(
        item = item,
        condition = when (type) {
            TYPE_TEMP_BELOW -> ClothesRule.TemperatureBelow(value, parseUnit(unit))
            TYPE_TEMP_ABOVE -> ClothesRule.TemperatureAbove(value, parseUnit(unit))
            TYPE_PRECIP_ABOVE -> ClothesRule.PrecipitationProbabilityAbove(value)
            else -> error("Unknown clothes rule type: $type")
        },
    )

    companion object {
        const val TYPE_TEMP_BELOW = "temp_below"
        const val TYPE_TEMP_ABOVE = "temp_above"
        const val TYPE_PRECIP_ABOVE = "precip_above"

        /** Falls back to Celsius for legacy data (`null`) or unrecognised tokens. */
        private fun parseUnit(raw: String?): TemperatureUnit =
            raw?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() }
                ?: TemperatureUnit.CELSIUS
    }
}

internal fun ClothesRule.toDto(): ClothesRuleDto = when (val c = condition) {
    is ClothesRule.TemperatureBelow ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_TEMP_BELOW, c.value, c.unit.name)
    is ClothesRule.TemperatureAbove ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_TEMP_ABOVE, c.value, c.unit.name)
    is ClothesRule.PrecipitationProbabilityAbove ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_PRECIP_ABOVE, c.percent)
}
