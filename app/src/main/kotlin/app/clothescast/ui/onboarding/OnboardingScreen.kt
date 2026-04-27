package app.clothescast.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clothescast.R
import app.clothescast.core.domain.model.Location
import app.clothescast.notification.NotificationPermission
import app.clothescast.ui.settings.KeyEntryFields
import app.clothescast.ui.settings.LinkifiedText
import app.clothescast.ui.settings.LocationSearchDialog

/**
 * First-run onboarding. Shown when the user lands on a fresh install missing any
 * of the things ClothesCast can't pick a sensible default for: notification
 * permission, a location, or a Gemini API key. Walks the user through each, then
 * drops them on Settings → Schedule so they can accept or change the default
 * 7am every-day schedule before reaching Today.
 *
 * "Skip for now" is session-only — the onboarding will reappear on cold launch
 * if the conditions still hold.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        OnboardingContent(
            geminiKeyConfigured = state.geminiKeyConfigured,
            location = state.location,
            useDeviceLocation = state.useDeviceLocation,
            padding = padding,
            onSetApiKey = viewModel::setApiKey,
            onSetUseDeviceLocation = viewModel::setUseDeviceLocation,
            onSelectLocation = viewModel::selectLocation,
            onSearchLocations = viewModel::searchLocations,
            onContinue = onContinue,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun OnboardingContent(
    geminiKeyConfigured: Boolean,
    location: Location?,
    useDeviceLocation: Boolean,
    padding: PaddingValues,
    onSetApiKey: (String) -> Unit,
    onSetUseDeviceLocation: (Boolean) -> Unit,
    onSelectLocation: (Location) -> Unit,
    onSearchLocations: suspend (String) -> List<Location>,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NotificationStep()
        HorizontalDivider()
        LocationStep(
            location = location,
            useDeviceLocation = useDeviceLocation,
            onSetUseDeviceLocation = onSetUseDeviceLocation,
            onSelectLocation = onSelectLocation,
            onSearchLocations = onSearchLocations,
        )
        HorizontalDivider()
        GeminiKeyStep(
            configured = geminiKeyConfigured,
            onSave = onSetApiKey,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.onboarding_skip)) }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.onboarding_continue)) }
        }
    }
}

@Composable
private fun NotificationStep() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationPermission.isGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check on resume so toggling permission from system Settings is reflected
        // when the user returns to onboarding.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = NotificationPermission.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StepCard(
        title = stringResource(R.string.onboarding_notifications_title),
        description = stringResource(R.string.onboarding_notifications_description),
        complete = granted,
    ) {
        if (!granted && NotificationPermission.isRequired()) {
            Button(
                onClick = { launcher.launch(NotificationPermission.MANIFEST_PERMISSION) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_notifications_grant)) }
        }
    }
}

@Composable
private fun LocationStep(
    location: Location?,
    useDeviceLocation: Boolean,
    onSetUseDeviceLocation: (Boolean) -> Unit,
    onSelectLocation: (Location) -> Unit,
    onSearchLocations: suspend (String) -> List<Location>,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(hasCoarseLocationPermission(context))
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionAsked = true
        // Mirrors LocationEditor: only flip the toggle on if foreground was granted,
        // otherwise the worker would consult the reader and silently fall through to
        // the configured fallback location every day.
        if (granted) onSetUseDeviceLocation(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check on resume so toggling permission from system Settings is reflected
        // when the user returns to onboarding.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasCoarseLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The step is "complete" if either branch is satisfied: we'll read device
    // location at notify time, or the user picked a fixed city.
    val configured = (useDeviceLocation && permissionGranted) || location != null

    StepCard(
        title = stringResource(R.string.onboarding_location_title),
        description = stringResource(R.string.onboarding_location_description),
        complete = configured,
    ) {
        if (configured) {
            val summary = when {
                useDeviceLocation && permissionGranted ->
                    stringResource(R.string.onboarding_location_using_device)
                location?.displayName != null -> location.displayName!!
                location != null -> "${location.latitude}, ${location.longitude}"
                else -> ""
            }
            if (summary.isNotEmpty()) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(
                onClick = {
                    if (permissionGranted) {
                        onSetUseDeviceLocation(true)
                    } else {
                        launcher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_location_grant)) }

            // Once the user has been asked once and denied, surface the manual
            // fallback as a primary path. Showing it from the start would compete
            // with the permission button; showing it only on denial keeps the
            // happier path uncluttered.
            if (permissionAsked && !permissionGranted) {
                Text(
                    text = stringResource(R.string.onboarding_location_denied_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { searchOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_location_enter_manual)) }
        }
    }

    if (searchOpen) {
        LocationSearchDialog(
            onDismiss = { searchOpen = false },
            onSelect = {
                onSelectLocation(it)
                searchOpen = false
            },
            onSearch = onSearchLocations,
        )
    }
}

private fun hasCoarseLocationPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
private fun GeminiKeyStep(
    configured: Boolean,
    onSave: (String) -> Unit,
) {
    StepCard(
        title = stringResource(R.string.onboarding_gemini_title),
        description = stringResource(R.string.onboarding_gemini_description),
        complete = configured,
    ) {
        if (!configured) {
            KeyEntryFields(
                configured = false,
                statusText = stringResource(R.string.settings_api_key_status_unset),
                placeholder = stringResource(R.string.settings_api_key_placeholder),
                saveLabel = stringResource(R.string.settings_api_key_save),
                clearLabel = stringResource(R.string.settings_api_key_clear),
                onSave = onSave,
                onClear = {},
            )
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    description: String,
    complete: Boolean,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (complete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.onboarding_step_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            LinkifiedText(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
