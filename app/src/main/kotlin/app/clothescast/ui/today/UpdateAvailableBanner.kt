package app.clothescast.ui.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clothescast.ClothesCastApplication
import app.clothescast.R
import app.clothescast.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Banner shown on the Today screen when Play has a newer build of the app.
 *
 * Only surfaces for Play-Store installs ([app.clothescast.update.InstallSource]
 * gates the check) and only when the available version code is higher than
 * the one the user has dismissed. Tapping **Update** kicks off Play's flexible
 * in-app update flow — the new APK downloads in the background, and once
 * [UpdateState.ReadyToInstall] arrives the banner switches to a **Restart**
 * prompt that calls [app.clothescast.update.AppUpdateChecker.completeUpdate].
 * Tapping **Not now** persists the available version code so the banner stays
 * hidden until a still-newer version is published.
 *
 * The two on-screen variants (Update vs. Restart) are driven entirely by the
 * checker's state: an in-session listener flips Available → ReadyToInstall
 * the moment the download finishes, and `refresh()` recovers the same state
 * across process recreation by consulting `installStatus()` directly. So a
 * user who backgrounded the app between download and install still sees the
 * Restart prompt on the next open instead of being stranded on "Update".
 *
 * Re-checks on `ON_RESUME` so a long-running app picks up newly-published
 * updates without a cold start, matching the [LastCrashBanner] lifecycle
 * pattern.
 */
@Composable
internal fun UpdateAvailableBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as ClothesCastApplication
    val checker = app.appUpdateChecker
    val settings = app.settingsRepository
    val coroutineScope = rememberCoroutineScope()

    val state by checker.state.collectAsStateWithLifecycle()
    val dismissedVersion by settings.dismissedUpdateVersion.collectAsStateWithLifecycle(initialValue = 0)

    LaunchedEffect(Unit) { checker.refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { checker.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        // Result is RESULT_OK / RESULT_CANCELED / RESULT_IN_APP_UPDATE_FAILED.
        // No action needed: a successful FLEXIBLE start means the download is
        // now in flight, and the checker's install-state listener flips state
        // to ReadyToInstall when DOWNLOADED arrives. Cancellation just leaves
        // the banner in its current "Update" state for next time.
    }

    val available = state as? UpdateState.Available
    val ready = state as? UpdateState.ReadyToInstall
    val versionCode = available?.availableVersionCode ?: ready?.availableVersionCode ?: return
    if (versionCode <= dismissedVersion) return

    UpdateAvailableBannerCard(
        modifier = modifier,
        downloadComplete = ready != null,
        onAction = {
            when {
                ready != null -> checker.completeUpdate()
                available != null -> checker.startFlexibleUpdate(available.info, launcher)
            }
        },
        onDismiss = {
            coroutineScope.launch { settings.setDismissedUpdateVersion(versionCode) }
        },
    )
}

@Composable
internal fun UpdateAvailableBannerCard(
    downloadComplete: Boolean,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // primaryContainer (not errorContainer): a stale build is informational,
    // not an error state. Sets the banner apart from LastCrashBanner /
    // LocationActionRequiredBanner / WorkStatusBanner.Failed which all use
    // errorContainer for genuine "something went wrong" cases.
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.today_update_available_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    if (downloadComplete) R.string.today_update_downloaded_body
                    else R.string.today_update_available_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.today_update_available_dismiss))
                }
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        stringResource(
                            if (downloadComplete) R.string.today_update_downloaded_action
                            else R.string.today_update_available_action,
                        ),
                    )
                }
            }
        }
    }
}
