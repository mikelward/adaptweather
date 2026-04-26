package app.clothescast.data

import app.clothescast.core.domain.model.WardrobeRule
import kotlinx.serialization.Serializable

/**
 * On-disk representation of a [WardrobeRule]. The domain type uses a sealed interface
 * for [WardrobeRule.Condition], which is awkward to serialize directly — this DTO
 * flattens it to (type, value) and round-trips through [toDomain] / [toDto].
 *
 * `type` strings are intentionally non-camelCase so they're stable identifiers in JSON
 * even if class names are renamed.
 */
@Serializable
internal data class WardrobeRuleDto(
    val item: String,
    val type: String,
    val value: Double,
) {
    fun toDomain(): WardrobeRule = WardrobeRule(
        item = item,
        condition = when (type) {
            TYPE_TEMP_BELOW -> WardrobeRule.TemperatureBelow(value)
            TYPE_TEMP_ABOVE -> WardrobeRule.TemperatureAbove(value)
            TYPE_PRECIP_ABOVE -> WardrobeRule.PrecipitationProbabilityAbove(value)
            else -> error("Unknown wardrobe rule type: $type")
        },
    )

    companion object {
        const val TYPE_TEMP_BELOW = "temp_below"
        const val TYPE_TEMP_ABOVE = "temp_above"
        const val TYPE_PRECIP_ABOVE = "precip_above"
    }
}

internal fun WardrobeRule.toDto(): WardrobeRuleDto = when (val c = condition) {
    is WardrobeRule.TemperatureBelow ->
        WardrobeRuleDto(item, WardrobeRuleDto.TYPE_TEMP_BELOW, c.celsius)
    is WardrobeRule.TemperatureAbove ->
        WardrobeRuleDto(item, WardrobeRuleDto.TYPE_TEMP_ABOVE, c.celsius)
    is WardrobeRule.PrecipitationProbabilityAbove ->
        WardrobeRuleDto(item, WardrobeRuleDto.TYPE_PRECIP_ABOVE, c.percent)
}
