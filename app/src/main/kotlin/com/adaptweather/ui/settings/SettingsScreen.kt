package com.adaptweather.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptweather.BuildConfig
import com.adaptweather.R
import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.Location
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.core.domain.model.TtsEngine
import com.adaptweather.core.domain.model.WardrobeRule
import com.adaptweather.work.FetchAndNotifyWorker
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        SettingsContent(
            state = state,
            padding = padding,
            onSetApiKey = viewModel::setApiKey,
            onClearApiKey = viewModel::clearApiKey,
            onSetSchedule = viewModel::setSchedule,
            onSetDeliveryMode = viewModel::setDeliveryMode,
            onSetTemperatureUnit = viewModel::setTemperatureUnit,
            onSetDistanceUnit = viewModel::setDistanceUnit,
            onAddWardrobeRule = viewModel::addWardrobeRule,
            onReplaceWardrobeRule = viewModel::replaceWardrobeRule,
            onDeleteWardrobeRule = viewModel::deleteWardrobeRule,
            onSelectLocation = viewModel::selectLocation,
            onClearLocation = viewModel::clearLocation,
            onSearchLocations = viewModel::searchLocations,
            onSetUseDeviceLocation = viewModel::setUseDeviceLocation,
            onSetTtsEngine = viewModel::setTtsEngine,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    padding: PaddingValues,
    onSetApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSetSchedule: (LocalTime, Set<DayOfWeek>) -> Unit,
    onSetDeliveryMode: (DeliveryMode) -> Unit,
    onSetTemperatureUnit: (TemperatureUnit) -> Unit,
    onSetDistanceUnit: (DistanceUnit) -> Unit,
    onAddWardrobeRule: (WardrobeRule) -> Unit,
    onReplaceWardrobeRule: (Int, WardrobeRule) -> Unit,
    onDeleteWardrobeRule: (Int) -> Unit,
    onSelectLocation: (Location) -> Unit,
    onClearLocation: () -> Unit,
    onSearchLocations: suspend (String) -> List<Location>,
    onSetUseDeviceLocation: (Boolean) -> Unit,
    onSetTtsEngine: (TtsEngine) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationPermissionBanner()
        ApiKeyCard(
            configured = state.apiKeyConfigured,
            onSave = onSetApiKey,
            onClear = onClearApiKey,
        )
        LocationCard(
            current = state.location,
            useDeviceLocation = state.useDeviceLocation,
            onSetUseDeviceLocation = onSetUseDeviceLocation,
            onSelect = onSelectLocation,
            onClear = onClearLocation,
            onSearch = onSearchLocations,
        )
        ScheduleCard(
            time = state.scheduleTime,
            days = state.scheduleDays,
            onChange = onSetSchedule,
        )
        DeliveryModeCard(state.deliveryMode, onSetDeliveryMode)
        TtsEngineCard(state.ttsEngine, onSetTtsEngine)
        TemperatureUnitCard(state.temperatureUnit, onSetTemperatureUnit)
        DistanceUnitCard(state.distanceUnit, onSetDistanceUnit)
        WardrobeRulesCard(
            rules = state.wardrobeRules,
            onAdd = onAddWardrobeRule,
            onReplace = onReplaceWardrobeRule,
            onDelete = onDeleteWardrobeRule,
        )
        if (BuildConfig.DEBUG) {
            DebugCard()
        }
        AboutCard()
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.settings_about_title)) {
        Text(
            text = stringResource(
                R.string.settings_about_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.settings_about_privacy),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = { openUrl(context, "https://github.com/mikelward/adaptweather") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_about_source)) }
        TextButton(
            onClick = { openUrl(context, "https://dontkillmyapp.com") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_about_dontkillmyapp)) }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LocationCard(
    current: Location?,
    useDeviceLocation: Boolean,
    onSetUseDeviceLocation: (Boolean) -> Unit,
    onSelect: (Location) -> Unit,
    onClear: () -> Unit,
    onSearch: suspend (String) -> List<Location>,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.settings_location_title)) {
        DeviceLocationToggleRow(
            checked = useDeviceLocation,
            onCheckedChange = onSetUseDeviceLocation,
        )

        Text(
            text = current?.displayName
                ?: current?.let { "${it.latitude}, ${it.longitude}" }
                ?: stringResource(R.string.settings_location_unset),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(
                if (useDeviceLocation) R.string.settings_location_description_device_on
                else R.string.settings_location_description,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { dialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (current == null) R.string.settings_location_set
                    else R.string.settings_location_change,
                ),
            )
        }
        if (current != null) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_location_clear))
            }
        }
    }

    if (dialogOpen) {
        LocationSearchDialog(
            onDismiss = { dialogOpen = false },
            onSelect = {
                onSelect(it)
                dialogOpen = false
            },
            onSearch = onSearch,
        )
    }
}

@Composable
private fun DeviceLocationToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val foregroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Only flip the toggle on if foreground was granted; otherwise the Worker would
        // hit our isPermissionGranted check, return null, and quietly fall through to
        // the settings location every day.
        onCheckedChange(granted)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_location_use_device),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { wantsOn ->
                if (!wantsOn) {
                    onCheckedChange(false)
                    return@Switch
                }
                if (hasCoarseLocationPermission(context)) {
                    onCheckedChange(true)
                } else {
                    foregroundLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )
    }

    if (checked && !hasBackgroundLocationPermission(context)) {
        TextButton(
            onClick = { openAppDetails(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_location_grant_background)) }
    }
}

private fun hasCoarseLocationPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocationPermission(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun openAppDetails(context: android.content.Context) {
    // Background location can't be requested via the runtime-permission dialog on
    // Android 11+ — it must be granted from the system app-info screen. We deep-link
    // there so the user only has to tap once to find it.
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LocationSearchDialog(
    onDismiss: () -> Unit,
    onSelect: (Location) -> Unit,
    onSearch: suspend (String) -> List<Location>,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Location>>(emptyList()) }
    var inFlight by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Location?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let(onSelect) },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.settings_location_search_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.settings_location_query_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    val coroutineScope = rememberCoroutineScope()
                    TextButton(
                        enabled = query.isNotBlank() && !inFlight,
                        onClick = {
                            coroutineScope.launch {
                                inFlight = true
                                error = null
                                try {
                                    results = onSearch(query)
                                    selected = null
                                } catch (t: Throwable) {
                                    error = t.message ?: t.javaClass.simpleName
                                    results = emptyList()
                                } finally {
                                    inFlight = false
                                }
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text(stringResource(R.string.settings_location_search)) }
                }

                when {
                    inFlight -> Text(stringResource(R.string.settings_location_searching))
                    error != null -> Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    results.isEmpty() && query.isNotBlank() ->
                        Text(stringResource(R.string.settings_location_no_results))
                    else -> results.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == result,
                                onClick = { selected = result },
                            )
                            Text(
                                text = result.displayName ?: "${result.latitude}, ${result.longitude}",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun WardrobeRulesCard(
    rules: List<WardrobeRule>,
    onAdd: (WardrobeRule) -> Unit,
    onReplace: (Int, WardrobeRule) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    SectionCard(title = stringResource(R.string.settings_wardrobe_title)) {
        Text(
            text = stringResource(R.string.settings_wardrobe_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_wardrobe_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rules.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider()
            WardrobeRuleRow(
                rule = rule,
                onEdit = { editIndex = index },
                onDelete = { onDelete(index) },
            )
        }
        Button(
            onClick = { addOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_wardrobe_add)) }
    }

    if (addOpen) {
        WardrobeRuleDialog(
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
        WardrobeRuleDialog(
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
private fun WardrobeRuleRow(
    rule: WardrobeRule,
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
        TextButton(onClick = onEdit) { Text(stringResource(R.string.settings_wardrobe_edit)) }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.settings_wardrobe_delete),
            )
        }
    }
}

@Composable
private fun describeCondition(condition: WardrobeRule.Condition): String = when (condition) {
    is WardrobeRule.TemperatureBelow ->
        stringResource(R.string.settings_wardrobe_cond_temp_below, condition.celsius)
    is WardrobeRule.TemperatureAbove ->
        stringResource(R.string.settings_wardrobe_cond_temp_above, condition.celsius)
    is WardrobeRule.PrecipitationProbabilityAbove ->
        stringResource(R.string.settings_wardrobe_cond_precip_above, condition.percent)
}

private enum class ConditionType { TEMP_BELOW, TEMP_ABOVE, PRECIP_ABOVE }

@Composable
private fun WardrobeRuleDialog(
    initial: WardrobeRule?,
    onDismiss: () -> Unit,
    onConfirm: (WardrobeRule) -> Unit,
) {
    var item by remember { mutableStateOf(initial?.item ?: "") }
    val initialType = when (initial?.condition) {
        is WardrobeRule.TemperatureBelow -> ConditionType.TEMP_BELOW
        is WardrobeRule.TemperatureAbove -> ConditionType.TEMP_ABOVE
        is WardrobeRule.PrecipitationProbabilityAbove -> ConditionType.PRECIP_ABOVE
        null -> ConditionType.TEMP_BELOW
    }
    var type by remember { mutableStateOf(initialType) }
    val initialValue = when (val c = initial?.condition) {
        is WardrobeRule.TemperatureBelow -> c.celsius
        is WardrobeRule.TemperatureAbove -> c.celsius
        is WardrobeRule.PrecipitationProbabilityAbove -> c.percent
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
                        ConditionType.TEMP_BELOW -> WardrobeRule.TemperatureBelow(parsedValue!!)
                        ConditionType.TEMP_ABOVE -> WardrobeRule.TemperatureAbove(parsedValue!!)
                        ConditionType.PRECIP_ABOVE -> WardrobeRule.PrecipitationProbabilityAbove(parsedValue!!)
                    }
                    onConfirm(WardrobeRule(item.trim(), condition))
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
                    if (initial == null) R.string.settings_wardrobe_dialog_add_title
                    else R.string.settings_wardrobe_dialog_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text(stringResource(R.string.settings_wardrobe_item_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_wardrobe_condition_label),
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
                                if (type == ConditionType.PRECIP_ABOVE) R.string.settings_wardrobe_value_label_percent
                                else R.string.settings_wardrobe_value_label_celsius,
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
    ConditionType.TEMP_BELOW -> R.string.settings_wardrobe_cond_type_temp_below
    ConditionType.TEMP_ABOVE -> R.string.settings_wardrobe_cond_type_temp_above
    ConditionType.PRECIP_ABOVE -> R.string.settings_wardrobe_cond_type_precip_above
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCard(
    time: LocalTime,
    days: Set<DayOfWeek>,
    onChange: (LocalTime, Set<DayOfWeek>) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_schedule_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_schedule_time_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            OutlinedButton(onClick = { pickerOpen = true }) {
                Text(text = TIME_FORMAT.format(time))
            }
        }

        Text(
            text = stringResource(R.string.settings_schedule_days_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Iterate week starting from MONDAY for European-style locales; Compose's
            // FilterChip wraps to multiple lines if needed. Picking a Set<DayOfWeek>
            // is semantic enough that the user-set order is irrelevant to scheduling.
            DayOfWeek.entries.forEach { dow ->
                val selected = dow in days
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) days - dow else days + dow
                        if (next.isNotEmpty()) onChange(time, next)
                    },
                    label = {
                        Text(text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                    },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }

    if (pickerOpen) {
        TimePickerDialog(
            initial = time,
            onDismiss = { pickerOpen = false },
            onConfirm = { newTime ->
                pickerOpen = false
                onChange(newTime, days)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = { TimePicker(state = state) },
    )
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun DebugCard() {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.settings_debug_title)) {
        Text(
            text = stringResource(R.string.settings_debug_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                FetchAndNotifyWorker.enqueueOneShot(context.applicationContext)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_debug_fire_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_debug_fire_now)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ApiKeyCard(
    configured: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_api_key_title)) {
        Text(
            text = if (configured) {
                stringResource(R.string.settings_api_key_status_set)
            } else {
                stringResource(R.string.settings_api_key_status_unset)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        var input by remember { mutableStateOf("") }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            visualTransformation = if (input.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onSave(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_api_key_save)) }

            if (configured) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_api_key_clear)) }
            }
        }
    }
}

@Composable
private fun DeliveryModeCard(
    selected: DeliveryMode,
    onSelect: (DeliveryMode) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_delivery_title)) {
        DeliveryMode.entries.forEach { mode ->
            RadioRow(
                label = stringResource(deliveryModeLabel(mode)),
                selected = mode == selected,
                onSelect = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun TtsEngineCard(
    selected: TtsEngine,
    onSelect: (TtsEngine) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_tts_engine_title)) {
        Text(
            text = stringResource(R.string.settings_tts_engine_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TtsEngine.entries.forEach { engine ->
            RadioRow(
                label = stringResource(ttsEngineLabel(engine)),
                selected = engine == selected,
                onSelect = { onSelect(engine) },
            )
        }
        if (selected == TtsEngine.GEMINI) {
            TestGeminiVoiceButton()
        }
    }
}

@Composable
private fun TestGeminiVoiceButton() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inFlight by remember { mutableStateOf(false) }
    Button(
        enabled = !inFlight,
        onClick = {
            coroutineScope.launch {
                inFlight = true
                val app = context.applicationContext as com.adaptweather.AdaptWeatherApplication
                try {
                    app.geminiTtsSpeaker.speak(
                        context.getString(R.string.settings_tts_test_sample),
                    )
                } catch (t: Throwable) {
                    val message = "${t.javaClass.simpleName}: ${t.message ?: "(no detail)"}"
                    android.util.Log.w("SettingsScreen", "Gemini TTS test failed", t)
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    inFlight = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (inFlight) R.string.settings_tts_test_in_flight
                else R.string.settings_tts_test,
            ),
        )
    }
}

@Composable
private fun TemperatureUnitCard(
    selected: TemperatureUnit,
    onSelect: (TemperatureUnit) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_temperature_unit_title)) {
        TemperatureUnit.entries.forEach { unit ->
            RadioRow(
                label = stringResource(temperatureUnitLabel(unit)),
                selected = unit == selected,
                onSelect = { onSelect(unit) },
            )
        }
    }
}

@Composable
private fun DistanceUnitCard(
    selected: DistanceUnit,
    onSelect: (DistanceUnit) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_distance_unit_title)) {
        DistanceUnit.entries.forEach { unit ->
            RadioRow(
                label = stringResource(distanceUnitLabel(unit)),
                selected = unit == selected,
                onSelect = { onSelect(unit) },
            )
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun deliveryModeLabel(mode: DeliveryMode): Int = when (mode) {
    DeliveryMode.NOTIFICATION_ONLY -> R.string.settings_delivery_notification_only
    DeliveryMode.TTS_ONLY -> R.string.settings_delivery_tts_only
    DeliveryMode.NOTIFICATION_AND_TTS -> R.string.settings_delivery_notification_and_tts
}

private fun ttsEngineLabel(engine: TtsEngine): Int = when (engine) {
    TtsEngine.DEVICE -> R.string.settings_tts_engine_device
    TtsEngine.GEMINI -> R.string.settings_tts_engine_gemini
}

private fun temperatureUnitLabel(unit: TemperatureUnit): Int = when (unit) {
    TemperatureUnit.CELSIUS -> R.string.settings_temperature_unit_celsius
    TemperatureUnit.FAHRENHEIT -> R.string.settings_temperature_unit_fahrenheit
}

private fun distanceUnitLabel(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> R.string.settings_distance_unit_kilometers
    DistanceUnit.MILES -> R.string.settings_distance_unit_miles
}
