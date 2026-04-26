package app.adaptweather.ui.onboarding

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
import app.adaptweather.R
import app.adaptweather.notification.NotificationPermission
import app.adaptweather.ui.settings.KeyEntryFields

/**
 * First-run onboarding. Shown when the user lands on a fresh install without
 * notification permission AND/OR without a Gemini API key. Walks the user through
 * the only two things ClothesCast can't pick a default for, then drops them on
 * Settings → Schedule so they can accept or change the default 7am every-day
 * schedule before reaching Today.
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
            padding = padding,
            onSetApiKey = viewModel::setApiKey,
            onContinue = onContinue,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun OnboardingContent(
    geminiKeyConfigured: Boolean,
    padding: PaddingValues,
    onSetApiKey: (String) -> Unit,
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
