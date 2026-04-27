package app.clothescast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.ClothesRule

/**
 * Read-only view of the user's current clothes rules. Editing is intentionally
 * locked down for now — the original UI accidentally exposed the *garment name*
 * as editable when the design intent was for users only to tune the
 * temperature / precipitation threshold per garment (with a fixed garment
 * preset list). The rules-redesign TODO on `ClothesRule` tracks the work to
 * bring editing back: fixed garment enum + per-garment threshold + an explicit
 * user-defined path with a translation hook.
 *
 * The `SettingsViewModel.{add,replace,delete}ClothesRule` methods are kept in
 * place because the redesign will need similar plumbing (and the ViewModel
 * already has tests covering them indirectly). They're just no longer wired
 * to any UI control.
 */
@Composable
internal fun ClothesContent(
    rules: List<ClothesRule>,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_clothes_title)) {
            Text(
                text = stringResource(R.string.settings_clothes_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            rules.forEachIndexed { index, rule ->
                if (index > 0) HorizontalDivider()
                ClothesRuleRow(rule)
            }
            Text(
                text = stringResource(R.string.settings_clothes_lockdown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClothesRuleRow(rule: ClothesRule) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(text = rule.item, style = MaterialTheme.typography.titleSmall)
        Text(
            text = describeCondition(rule.condition),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun describeCondition(condition: ClothesRule.Condition): String = when (condition) {
    is ClothesRule.TemperatureBelow ->
        stringResource(R.string.settings_clothes_cond_temp_below, condition.celsius)
    is ClothesRule.TemperatureAbove ->
        stringResource(R.string.settings_clothes_cond_temp_above, condition.celsius)
    is ClothesRule.PrecipitationProbabilityAbove ->
        stringResource(R.string.settings_clothes_cond_precip_above, condition.percent)
}
