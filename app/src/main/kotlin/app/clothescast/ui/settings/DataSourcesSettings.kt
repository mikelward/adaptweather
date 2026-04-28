package app.clothescast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.Location
import kotlinx.coroutines.launch

@Composable
internal fun DataSourcesContent(
    location: Location?,
    useDeviceLocation: Boolean,
    useCalendarEvents: Boolean,
    padding: PaddingValues,
    onSetUseDeviceLocation: (Boolean) -> Unit,
    onSelectLocation: (Location) -> Unit,
    onClearLocation: () -> Unit,
    onSearchLocations: suspend (String) -> List<Location>,
    onSetUseCalendarEvents: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocationCard(
            current = location,
            useDeviceLocation = useDeviceLocation,
            onSetUseDeviceLocation = onSetUseDeviceLocation,
            onSelect = onSelectLocation,
            onClear = onClearLocation,
            onSearch = onSearchLocations,
        )
        CalendarCard(
            useEvents = useCalendarEvents,
            onSetUseEvents = onSetUseCalendarEvents,
        )
    }
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
    // Track background-permission state so the "Grant always-on" prompt actually
    // disappears when the user returns from system Settings having granted it —
    // without an on-resume re-check the composable keeps the stale value from
    // first composition and the button lingers forever.
    var backgroundGranted by remember {
        mutableStateOf(hasBackgroundLocationPermission(context))
    }
    var rationaleOpen by remember { mutableStateOf(false) }
    var backgroundDeniedOpen by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                backgroundGranted = hasBackgroundLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Background launcher: on Android 11+ this auto-deep-links into the system
    // Settings location picker; on Android 10 it shows the inline dialog. If the
    // user ends up granting only foreground we surface a follow-up dialog so
    // they understand the daily worker will fall back to the saved location.
    val backgroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        backgroundGranted = granted
        if (!granted) backgroundDeniedOpen = true
    }

    val foregroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Only flip the toggle on if foreground was granted; otherwise the Worker would
        // hit our isPermissionGranted check, return null, and quietly fall through to
        // the settings location every day.
        onCheckedChange(granted)
        if (granted && !hasBackgroundLocationPermission(context) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        ) {
            backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
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
                when {
                    !hasCoarseLocationPermission(context) -> {
                        // Pre-Q has no separate "Allow all the time" choice — coarse
                        // grant *is* always-on — so the rationale dialog's copy doesn't
                        // apply. Skip straight to the foreground request there; the
                        // background launcher + denied dialog also no-op on pre-Q
                        // because hasBackgroundLocationPermission() returns true.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rationaleOpen = true
                        } else {
                            foregroundLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                    !backgroundGranted -> {
                        // Only reachable on Q+ — hasBackgroundLocationPermission()
                        // returns true on pre-Q so backgroundGranted is always true
                        // there. Foreground was granted in a previous attempt; chase
                        // the missing background grant directly.
                        onCheckedChange(true)
                        backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    else -> onCheckedChange(true)
                }
            },
        )
    }

    if (checked && !backgroundGranted) {
        TextButton(
            onClick = { openAppDetails(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_location_grant_background)) }
    }

    if (rationaleOpen) {
        AlertDialog(
            onDismissRequest = { rationaleOpen = false },
            title = { Text(stringResource(R.string.settings_location_rationale_title)) },
            text = { Text(stringResource(R.string.settings_location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    rationaleOpen = false
                    foregroundLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text(stringResource(R.string.settings_location_rationale_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { rationaleOpen = false }) {
                    Text(stringResource(R.string.settings_location_rationale_dismiss))
                }
            },
        )
    }

    if (backgroundDeniedOpen) {
        AlertDialog(
            onDismissRequest = { backgroundDeniedOpen = false },
            title = { Text(stringResource(R.string.settings_location_background_denied_title)) },
            text = { Text(stringResource(R.string.settings_location_background_denied_body)) },
            confirmButton = {
                TextButton(onClick = {
                    backgroundDeniedOpen = false
                    openAppDetails(context)
                }) { Text(stringResource(R.string.settings_location_background_denied_open)) }
            },
            dismissButton = {
                TextButton(onClick = { backgroundDeniedOpen = false }) {
                    Text(stringResource(R.string.settings_location_background_denied_keep))
                }
            },
        )
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

/**
 * Search-by-city-name dialog used by the data-sources page and the onboarding
 * screen's location step. Shows a query field, runs [onSearch] on demand, and
 * lets the user pick exactly one of the geocoder results.
 */
@Composable
internal fun LocationSearchDialog(
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
private fun CalendarCard(
    useEvents: Boolean,
    onSetUseEvents: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(app.clothescast.calendar.CalendarPermission.isGranted(context))
    }

    val currentUseEvents by rememberUpdatedState(useEvents)
    val currentOnSetUseEvents by rememberUpdatedState(onSetUseEvents)

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check on resume so the toggle reflects whatever the user did in system
        // Settings while we were backgrounded. If permission was revoked, also flip
        // the persisted pref off so the worker stops consulting the reader.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = app.clothescast.calendar.CalendarPermission.isGranted(context)
                permissionGranted = granted
                if (!granted && currentUseEvents) {
                    currentOnSetUseEvents(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        // Only flip the toggle on if the user granted; otherwise the worker would
        // consult the reader, get an empty list every morning, and silently log a
        // permission-denied warning forever.
        onSetUseEvents(granted)
    }

    SectionCard(title = stringResource(R.string.settings_calendar_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_calendar_use_events),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = useEvents && permissionGranted,
                onCheckedChange = { wantsOn ->
                    if (!wantsOn) {
                        onSetUseEvents(false)
                        return@Switch
                    }
                    if (permissionGranted) {
                        onSetUseEvents(true)
                    } else {
                        launcher.launch(app.clothescast.calendar.CalendarPermission.MANIFEST_PERMISSION)
                    }
                },
            )
        }
        Text(
            text = stringResource(
                if (useEvents && permissionGranted) R.string.settings_calendar_description_on
                else R.string.settings_calendar_description_off,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (useEvents && !permissionGranted) {
            Text(
                text = stringResource(R.string.settings_calendar_open_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = { openAppDetails(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_calendar_grant_permission)) }
        }
    }
}
