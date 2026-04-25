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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.work.FetchAndNotifyWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        SettingsContent(
            state = state,
            padding = padding,
            onSetApiKey = viewModel::setApiKey,
            onClearApiKey = viewModel::clearApiKey,
            onSetDeliveryMode = viewModel::setDeliveryMode,
            onSetTemperatureUnit = viewModel::setTemperatureUnit,
            onSetDistanceUnit = viewModel::setDistanceUnit,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    padding: PaddingValues,
    onSetApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSetDeliveryMode: (DeliveryMode) -> Unit,
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
        NotificationPermissionBanner()
        ApiKeyCard(
            configured = state.apiKeyConfigured,
            onSave = onSetApiKey,
            onClear = onClearApiKey,
        )
        DeliveryModeCard(state.deliveryMode, onSetDeliveryMode)
        TemperatureUnitCard(state.temperatureUnit, onSetTemperatureUnit)
        DistanceUnitCard(state.distanceUnit, onSetDistanceUnit)
        if (BuildConfig.DEBUG) {
            DebugCard()
        }
    }
}

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

private fun temperatureUnitLabel(unit: TemperatureUnit): Int = when (unit) {
    TemperatureUnit.CELSIUS -> R.string.settings_temperature_unit_celsius
    TemperatureUnit.FAHRENHEIT -> R.string.settings_temperature_unit_fahrenheit
}

private fun distanceUnitLabel(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> R.string.settings_distance_unit_kilometers
    DistanceUnit.MILES -> R.string.settings_distance_unit_miles
}
