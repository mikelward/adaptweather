package app.clothescast.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.clothescast.diag.DiagLog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper around Play's [AppUpdateManager] for the Today-screen update
 * banner.
 *
 * On non-Play installs (sideload, F-Droid) the check short-circuits to
 * [UpdateState.UpToDate] without touching Play Services — see [InstallSource].
 * Any failure from `requestAppUpdateInfo` (no Play Services, airplane mode,
 * Play SDK init failure) also collapses to UpToDate so the banner stays
 * hidden rather than surfacing a confusing error: a missing update prompt
 * is the right default when we can't determine the answer.
 *
 * Two states drive the banner: [UpdateState.Available] (a newer build is
 * published — show "Update") and [UpdateState.ReadyToInstall] (a flexible
 * download has finished — show "Restart"). The latter is reachable both
 * within a session (the install-state listener fires when the download
 * completes while the app is running) and across process recreation
 * ([refresh] consults `installStatus()` directly, which handles the case
 * where the user backgrounded the app between download and install — Play
 * returns `installStatus == DOWNLOADED` with `updateAvailability ==
 * DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`, so a naive `updateAvailability`
 * check alone would lose the user's downloaded update).
 */
class AppUpdateChecker(private val context: Context) {
    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(context) }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.NotChecked)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile private var listenerRegistered = false
    private val listener = InstallStateUpdatedListener { installState ->
        // Live-session promotion: the moment the background download finishes,
        // flip Available → ReadyToInstall so the banner switches from "Update"
        // to "Restart" without waiting for the next ON_RESUME refresh. Across
        // process recreation, `refresh()` is the source of truth instead.
        if (installState.installStatus() == InstallStatus.DOWNLOADED) {
            (_state.value as? UpdateState.Available)?.let { available ->
                _state.value = UpdateState.ReadyToInstall(
                    availableVersionCode = available.availableVersionCode,
                    info = available.info,
                )
            }
        }
    }

    suspend fun refresh() {
        if (!InstallSource.isFromPlayStore(context)) {
            _state.value = UpdateState.UpToDate
            return
        }
        val info = runCatching { manager.requestAppUpdateInfo() }
            .onFailure { DiagLog.w(TAG, "requestAppUpdateInfo failed", it) }
            .getOrNull()
        if (info == null) {
            _state.value = UpdateState.UpToDate
            return
        }
        ensureListenerRegistered()
        _state.value = when {
            // Already-downloaded path: a previous session's flexible download
            // finished while the process was dead. Play returns
            // `updateAvailability == DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`
            // here (not UPDATE_AVAILABLE), so check installStatus() first or
            // we'd silently strand a downloaded update behind a hidden banner.
            info.installStatus() == InstallStatus.DOWNLOADED ->
                UpdateState.ReadyToInstall(info.availableVersionCode(), info)
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                UpdateState.Available(info.availableVersionCode(), info)
            else -> UpdateState.UpToDate
        }
    }

    fun startFlexibleUpdate(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        manager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
        )
    }

    /** Triggers the install once the FLEXIBLE download has completed. */
    fun completeUpdate() {
        manager.completeUpdate()
    }

    private fun ensureListenerRegistered() {
        if (listenerRegistered) return
        manager.registerListener(listener)
        listenerRegistered = true
    }

    companion object {
        private const val TAG = "AppUpdateChecker"
    }
}

sealed interface UpdateState {
    data object NotChecked : UpdateState
    data object UpToDate : UpdateState
    data class Available(val availableVersionCode: Int, val info: AppUpdateInfo) : UpdateState
    data class ReadyToInstall(val availableVersionCode: Int, val info: AppUpdateInfo) : UpdateState
}
