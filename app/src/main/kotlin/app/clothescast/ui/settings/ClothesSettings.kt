package app.clothescast.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.Garment
import kotlin.math.roundToInt

/**
 * Lists the user's clothes rules and lets them add / edit / delete one. The
 * garment is picked from a fixed [Garment] dropdown rather than free-form
 * text — free-form names defeated translation in the German insight prose
 * (see PR that locked editing down). The dropdown labels are localised via
 * [garmentLabelRes]; the stored rule's `item` field is always the en-US
 * key (e.g. "sweater"), which the German phraser then translates to
 * "Pullover" at insight-render time.
 */
@Composable
internal fun ClothesContent(
    rules: List<ClothesRule>,
    padding: PaddingValues,
    onAdd: (ClothesRule) -> Unit,
    onReplace: (Int, ClothesRule) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClothesRulesCard(rules, onAdd, onReplace, onDelete)
    }
}

@Composable
private fun ClothesRulesCard(
    rules: List<ClothesRule>,
    onAdd: (ClothesRule) -> Unit,
    onReplace: (Int, ClothesRule) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    SectionCard(title = stringResource(R.string.settings_clothes_title)) {
        Text(
            text = stringResource(R.string.settings_clothes_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_clothes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rules.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider()
            ClothesRuleRow(
                rule = rule,
                onEdit = { editIndex = index },
                onDelete = { onDelete(index) },
            )
        }
        Button(
            onClick = { addOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_clothes_add)) }
    }

    if (addOpen) {
        ClothesRuleDialog(
            initial = null,
            onDismiss = { addOpen = false },
            onConfirm = {
                addOpen = false
                onAdd(it)
            },
        )
    }

    val editing = editIndex
    if (editing != null && editing in rules.indices) {
        ClothesRuleDialog(
            initial = rules[editing],
            onDismiss = { editIndex = null },
            onConfirm = {
                onReplace(editing, it)
                editIndex = null
            },
        )
    }
}

@Composable
private fun ClothesRuleRow(
    rule: ClothesRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Show the localised garment name for catalog items; fall back to
            // the raw stored key for any older / custom items so the user can
            // still see and delete them.
            val garment = Garment.fromKey(rule.item)
            val label = if (garment != null) stringResource(garmentLabelRes(garment)) else rule.item
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = describeCondition(rule.condition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.settings_clothes_edit)) }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.settings_clothes_delete),
            )
        }
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

private enum class ConditionType(@StringRes val labelRes: Int) {
    TEMP_BELOW(R.string.settings_clothes_cond_type_temp_below),
    TEMP_ABOVE(R.string.settings_clothes_cond_type_temp_above),
    PRECIP_ABOVE(R.string.settings_clothes_cond_type_precip_above),
}

/** Maps a [Garment] enum entry to its localised display label resource. */
@StringRes
private fun garmentLabelRes(garment: Garment): Int = when (garment) {
    Garment.SWEATER -> R.string.garment_sweater
    Garment.HOODIE -> R.string.garment_hoodie
    Garment.JACKET -> R.string.garment_jacket
    Garment.COAT -> R.string.garment_coat
    Garment.TSHIRT -> R.string.garment_tshirt
    Garment.SHIRT -> R.string.garment_shirt
    Garment.SHORTS -> R.string.garment_shorts
    Garment.PANTS -> R.string.garment_pants
    Garment.JEANS -> R.string.garment_jeans
}

@Composable
private fun ClothesRuleDialog(
    initial: ClothesRule?,
    onDismiss: () -> Unit,
    onConfirm: (ClothesRule) -> Unit,
) {
    // Pre-select the initial rule's garment when editing; default to SWEATER
    // when adding (the most common cold-weather rule). Items not in the catalog
    // (older free-form rules) preselect SWEATER too — the user can pick any
    // catalog garment and confirm to migrate the rule onto a known key.
    var garment by remember {
        mutableStateOf(initial?.item?.let(Garment::fromKey) ?: Garment.SWEATER)
    }
    val initialType = when (initial?.condition) {
        is ClothesRule.TemperatureBelow -> ConditionType.TEMP_BELOW
        is ClothesRule.TemperatureAbove -> ConditionType.TEMP_ABOVE
        is ClothesRule.PrecipitationProbabilityAbove -> ConditionType.PRECIP_ABOVE
        null -> ConditionType.TEMP_BELOW
    }
    var type by remember { mutableStateOf(initialType) }
    val initialValue = when (val c = initial?.condition) {
        is ClothesRule.TemperatureBelow -> c.celsius
        is ClothesRule.TemperatureAbove -> c.celsius
        is ClothesRule.PrecipitationProbabilityAbove -> c.percent
        null -> 18.0
    }
    // Whole-number input only. Keeps the row label (rendered with %.0f) in
    // sync with what the user typed and dodges locale-specific decimal
    // separators (German keyboards default to "18,5", which Double.toDoubleOrNull
    // rejects). Existing rules with fractional values round to the nearest
    // int when first opened — defaults are all integers so this is purely
    // defensive for legacy data.
    var valueText by remember { mutableStateOf(initialValue.roundToInt().toString()) }

    val parsedValue = valueText.toIntOrNull()
    // Precip rules are bounded 0–100 (it's a probability percentage); temperature
    // rules can be any int. Disable confirm when out of range so the rule can't
    // be saved with a nonsense threshold.
    val valueValid = parsedValue != null &&
        (type != ConditionType.PRECIP_ABOVE || parsedValue in 0..100)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = valueValid,
                onClick = {
                    val v = parsedValue!!.toDouble()
                    val condition = when (type) {
                        ConditionType.TEMP_BELOW -> ClothesRule.TemperatureBelow(v)
                        ConditionType.TEMP_ABOVE -> ClothesRule.TemperatureAbove(v)
                        ConditionType.PRECIP_ABOVE -> ClothesRule.PrecipitationProbabilityAbove(v)
                    }
                    onConfirm(ClothesRule(garment.itemKey, condition))
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.settings_clothes_dialog_add_title
                    else R.string.settings_clothes_dialog_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GarmentDropdown(
                    selected = garment,
                    onSelect = { garment = it },
                )
                Text(
                    text = stringResource(R.string.settings_clothes_condition_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                ConditionType.entries.forEach { entry ->
                    RadioRow(
                        label = stringResource(entry.labelRes),
                        selected = type == entry,
                        onSelect = { type = entry },
                    )
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = {
                        Text(
                            stringResource(
                                if (type == ConditionType.PRECIP_ABOVE) {
                                    R.string.settings_clothes_value_label_precip
                                } else {
                                    R.string.settings_clothes_value_label_temp_c
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !valueValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarmentDropdown(
    selected: Garment,
    onSelect: (Garment) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(garmentLabelRes(selected)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_clothes_item_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Garment.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(garmentLabelRes(entry))) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}
