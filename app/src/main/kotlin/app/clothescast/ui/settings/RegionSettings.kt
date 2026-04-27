package app.clothescast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureUnit

@Composable
internal fun RegionContent(
    region: Region,
    temperatureUnit: TemperatureUnit,
    distanceUnit: DistanceUnit,
    padding: PaddingValues,
    onSetRegion: (Region) -> Unit,
    onSetTemperatureUnit: (TemperatureUnit) -> Unit,
    onSetDistanceUnit: (DistanceUnit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_region_language_title)) {
            Region.entries.forEach { option ->
                RadioRow(
                    label = stringResource(regionLabel(option)),
                    selected = option == region,
                    onSelect = { onSetRegion(option) },
                )
            }
        }
        SectionCard(title = stringResource(R.string.settings_temperature_unit_title)) {
            TemperatureUnit.entries.forEach { unit ->
                RadioRow(
                    label = stringResource(temperatureUnitLabel(unit)),
                    selected = unit == temperatureUnit,
                    onSelect = { onSetTemperatureUnit(unit) },
                )
            }
        }
        SectionCard(title = stringResource(R.string.settings_distance_unit_title)) {
            DistanceUnit.entries.forEach { unit ->
                RadioRow(
                    label = stringResource(distanceUnitLabel(unit)),
                    selected = unit == distanceUnit,
                    onSelect = { onSetDistanceUnit(unit) },
                )
            }
        }
    }
}

private fun regionLabel(region: Region): Int = when (region) {
    Region.SYSTEM -> R.string.settings_region_language_system
    Region.EN_US -> R.string.settings_region_language_en_us
    Region.EN_GB -> R.string.settings_region_language_en_gb
    Region.EN_AU -> R.string.settings_region_language_en_au
}

private fun temperatureUnitLabel(unit: TemperatureUnit): Int = when (unit) {
    TemperatureUnit.CELSIUS -> R.string.settings_temperature_unit_celsius
    TemperatureUnit.FAHRENHEIT -> R.string.settings_temperature_unit_fahrenheit
}

private fun distanceUnitLabel(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> R.string.settings_distance_unit_kilometers
    DistanceUnit.MILES -> R.string.settings_distance_unit_miles
}
