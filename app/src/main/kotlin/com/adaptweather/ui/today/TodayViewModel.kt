package com.adaptweather.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.adaptweather.core.domain.model.Insight
import com.adaptweather.data.InsightCache
import com.adaptweather.work.FetchAndNotifyWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayState(
    val insight: Insight? = null,
    val workStatus: WorkStatus = WorkStatus.Idle,
)

sealed class WorkStatus {
    data object Idle : WorkStatus()
    data object Running : WorkStatus()
    data class Failed(val reason: String?, val detail: String?) : WorkStatus()
}

class TodayViewModel(
    insightCache: InsightCache,
    workManager: WorkManager,
) : ViewModel() {
    val state: StateFlow<TodayState> = combine(
        insightCache.latest,
        workManager.getWorkInfosForUniqueWorkFlow(FetchAndNotifyWorker.UNIQUE_WORK_NAME),
    ) { insight, workInfos ->
        TodayState(insight = insight, workStatus = workInfos.toStatus())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    private fun List<WorkInfo>.toStatus(): WorkStatus {
        // Most recent terminal or running entry — WorkManager may keep multiple history
        // rows for the same unique name. We take the first one whose state matters.
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

    class Factory(
        private val insightCache: InsightCache,
        private val workManager: WorkManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return TodayViewModel(insightCache, workManager) as T
        }
    }
}
