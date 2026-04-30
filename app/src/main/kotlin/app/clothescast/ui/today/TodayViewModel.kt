package app.clothescast.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.clothescast.core.domain.model.Fact
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.data.InsightCache
import app.clothescast.data.SettingsRepository
import app.clothescast.work.FetchAndNotifyWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class TodayState(
    val insight: Insight? = null,
    val workStatus: WorkStatus = WorkStatus.Idle,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val region: Region = Region.SYSTEM,
    // Window boundaries used by manual Refresh to decide TODAY vs TONIGHT.
    // Default to the same 7am / 7pm boundaries Schedule uses out of the box;
    // the ViewModel overwrites these with the user's actual schedule times.
    val morningTime: LocalTime = LocalTime.of(7, 0),
    val tonightTime: LocalTime = LocalTime.of(19, 0),
    // Inputs the Today screen needs to compute whether a "set up location"
    // prompt should appear. Permission state is checked in the Composable
    // (it depends on Context and needs an on-resume re-check); the ViewModel
    // just exposes the prefs side of the equation.
    val useDeviceLocation: Boolean = false,
    val hasFallbackLocation: Boolean = false,
    /** Live cutoffs the rationale dialog reads to render its current threshold value
     * and the `−1°` / `+1°` controls. The cached [Insight.outfitRationale] still
     * carries the threshold values that *were* in effect at insight-generation time
     * (which might differ from these if the user has nudged a knob since); the
     * dialog prefers these for display so the controls stay honest. */
    val outfitThresholds: OutfitSuggestion.Thresholds = OutfitSuggestion.Thresholds.DEFAULT,
)

sealed class WorkStatus {
    data object Idle : WorkStatus()
    data object Running : WorkStatus()
    data class Failed(val reason: String?, val detail: String?) : WorkStatus()
}

class TodayViewModel(
    insightCache: InsightCache,
    workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    // Combine status across both unique-work names so the spinner / failure
    // banner reflects an in-flight tonight refresh too — the Refresh button
    // routes to TONIGHT when it's tapped between 19:00 and 07:00.
    val state: StateFlow<TodayState> = combine(
        insightCache.latest,
        workManager.getWorkInfosForUniqueWorkFlow(FetchAndNotifyWorker.UNIQUE_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(FetchAndNotifyWorker.UNIQUE_WORK_NAME_TONIGHT),
        settingsRepository.preferences,
    ) { insight, todayInfos, tonightInfos, prefs ->
        TodayState(
            insight = insight,
            workStatus = mergeStatus(todayInfos.toStatus(), tonightInfos.toStatus()),
            temperatureUnit = prefs.temperatureUnit,
            region = prefs.region,
            morningTime = prefs.schedule.time,
            tonightTime = prefs.tonightSchedule.time,
            useDeviceLocation = prefs.useDeviceLocation,
            hasFallbackLocation = prefs.location != null,
            outfitThresholds = prefs.outfitThresholds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /**
     * Nudges one of [OutfitSuggestion.Thresholds]'s knobs by [deltaC] degrees Celsius.
     * Wired to the rationale dialog's `−1°` / `+1°` controls. The read-modify-write
     * is delegated to [SettingsRepository.adjustOutfitThreshold] which performs it
     * inside a single `DataStore.edit { … }` transaction — rapid taps each read the
     * latest persisted value, so taps don't collapse into one even when several
     * coroutines are in flight. Final value is clamped to the documented sanity
     * range.
     */
    fun adjustOutfitThreshold(kind: Fact.ThresholdKind, deltaC: Double) {
        viewModelScope.launch { settingsRepository.adjustOutfitThreshold(kind, deltaC) }
    }

    /** Reverts every threshold to [OutfitSuggestion.Thresholds.DEFAULT]. */
    fun resetOutfitThresholds() {
        viewModelScope.launch { settingsRepository.resetOutfitThresholds() }
    }

    private fun List<WorkInfo>.toStatus(): WorkStatus {
        // Most recent terminal or running entry — WorkManager may keep multiple history
        // rows for the same unique name. We take the first one whose state matters.
        // Caller resolves cross-chain precedence in [mergeStatus]; runAttemptCount
        // is only comparable within a single unique-work chain.
        if (isEmpty()) return WorkStatus.Idle
        val latest = maxByOrNull { it.runAttemptCount } ?: first()
        return when (latest.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> WorkStatus.Running
            WorkInfo.State.FAILED -> WorkStatus.Failed(
                reason = latest.outputData.getString(FetchAndNotifyWorker.KEY_REASON),
                detail = latest.outputData.getString(FetchAndNotifyWorker.KEY_REASON_DETAIL),
            )
            else -> WorkStatus.Idle
        }
    }

    // Explicit precedence: any in-flight refresh keeps the spinner up; otherwise
    // surface the most recent failure if either chain ended in one; otherwise
    // idle. Avoids comparing runAttemptCount across two unrelated WorkManager
    // unique-work histories — those counters are per-chain and would let a stale
    // FAILED entry from one chain mask a live RUNNING entry on the other.
    private fun mergeStatus(a: WorkStatus, b: WorkStatus): WorkStatus = when {
        a is WorkStatus.Running || b is WorkStatus.Running -> WorkStatus.Running
        a is WorkStatus.Failed -> a
        b is WorkStatus.Failed -> b
        else -> WorkStatus.Idle
    }

    class Factory(
        private val insightCache: InsightCache,
        private val workManager: WorkManager,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return TodayViewModel(insightCache, workManager, settingsRepository) as T
        }
    }
}
