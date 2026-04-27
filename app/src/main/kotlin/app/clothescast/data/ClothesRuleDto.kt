package app.clothescast.data

import app.clothescast.core.domain.model.ClothesRule
import kotlinx.serialization.Serializable

/**
 * On-disk representation of a [ClothesRule]. The domain type uses a sealed interface
 * for [ClothesRule.Condition], which is awkward to serialize directly — this DTO
 * flattens it to (type, value) and round-trips through [toDomain] / [toDto].
 *
 * `type` strings are intentionally non-camelCase so they're stable identifiers in JSON
 * even if class names are renamed.
 */
@Serializable
internal data class ClothesRuleDto(
    val item: String,
    val type: String,
    val value: Double,
) {
    fun toDomain(): ClothesRule = ClothesRule(
        item = item,
        condition = when (type) {
            TYPE_TEMP_BELOW -> ClothesRule.TemperatureBelow(value)
            TYPE_TEMP_ABOVE -> ClothesRule.TemperatureAbove(value)
            TYPE_PRECIP_ABOVE -> ClothesRule.PrecipitationProbabilityAbove(value)
            else -> error("Unknown clothes rule type: $type")
        },
    )

    companion object {
        const val TYPE_TEMP_BELOW = "temp_below"
        const val TYPE_TEMP_ABOVE = "temp_above"
        const val TYPE_PRECIP_ABOVE = "precip_above"
    }
}

internal fun ClothesRule.toDto(): ClothesRuleDto = when (val c = condition) {
    is ClothesRule.TemperatureBelow ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_TEMP_BELOW, c.celsius)
    is ClothesRule.TemperatureAbove ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_TEMP_ABOVE, c.celsius)
    is ClothesRule.PrecipitationProbabilityAbove ->
        ClothesRuleDto(item, ClothesRuleDto.TYPE_PRECIP_ABOVE, c.percent)
}
