package com.adaptweather.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adaptweather.core.domain.model.Insight
import com.adaptweather.data.InsightCache
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TodayState(val insight: Insight? = null)

class TodayViewModel(insightCache: InsightCache) : ViewModel() {
    val state: StateFlow<TodayState> = insightCache.latest
        .map { TodayState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    class Factory(private val insightCache: InsightCache) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                "Unknown ViewModel: ${modelClass.name}"
            }
            return TodayViewModel(insightCache) as T
        }
    }
}
