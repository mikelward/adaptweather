package app.clothescast.ui.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
            Text(text = rule.item, style = MaterialTheme.typography.titleSmall)
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

private enum class ConditionType { TEMP_BELOW, TEMP_ABOVE, PRECIP_ABOVE }

@Composable
private fun ClothesRuleDialog(
    initial: ClothesRule?,
    onDismiss: () -> Unit,
    onConfirm: (ClothesRule) -> Unit,
) {
    var item by remember { mutableStateOf(initial?.item ?: "") }
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
    var valueText by remember { mutableStateOf(initialValue.toString()) }

    val parsedValue = valueText.toDoubleOrNull()
    val canConfirm = item.isNotBlank() && parsedValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val condition = when (type) {
                        ConditionType.TEMP_BELOW -> ClothesRule.TemperatureBelow(parsedValue!!)
                        ConditionType.TEMP_ABOVE -> ClothesRule.TemperatureAbove(parsedValue!!)
                        ConditionType.PRECIP_ABOVE -> ClothesRule.PrecipitationProbabilityAbove(parsedValue!!)
                    }
                    onConfirm(ClothesRule(item.trim(), condition))
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
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text(stringResource(R.string.settings_clothes_item_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_clothes_condition_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ConditionType.entries.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(
                            text = stringResource(conditionTypeLabel(t)),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = {
                        Text(
                            stringResource(
                                if (type == ConditionType.PRECIP_ABOVE) R.string.settings_clothes_value_label_percent
                                else R.string.settings_clothes_value_label_celsius,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = parsedValue == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private fun conditionTypeLabel(type: ConditionType): Int = when (type) {
    ConditionType.TEMP_BELOW -> R.string.settings_clothes_cond_type_temp_below
    ConditionType.TEMP_ABOVE -> R.string.settings_clothes_cond_type_temp_above
    ConditionType.PRECIP_ABOVE -> R.string.settings_clothes_cond_type_precip_above
}
