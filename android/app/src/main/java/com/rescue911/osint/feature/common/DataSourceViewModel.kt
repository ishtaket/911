package com.rescue911.osint.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescue911.osint.core.backend.BackendStatus
import com.rescue911.osint.core.backend.BackendStatusMonitor
import com.rescue911.osint.data.preferences.AppPreferences
import com.rescue911.osint.ui.components.DataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Derives the user-visible "Data source" badge state from the two underlying
 * sources of truth:
 *
 *   - [AppPreferences.mockMode]  (operator preference)
 *   - [BackendStatusMonitor.state] (last health probe)
 *
 * Badge values map to the dispatcher's actual routing in
 * [com.rescue911.osint.data.repository.Rescue911RepositoryDispatcher]:
 *
 *   mock=true               → MOCK
 *   mock=false + Online     → BACKEND
 *   mock=false + Offline    → FALLBACK   (dispatcher silently uses mock)
 *   mock=false + Checking/? → CHECKING
 */
@HiltViewModel
class DataSourceViewModel @Inject constructor(
    prefs: AppPreferences,
    monitor: BackendStatusMonitor,
) : ViewModel() {

    init {
        // Probe backend once on app open so the badge is honest from the start.
        viewModelScope.launch { runCatching { monitor.check() } }
    }

    val source: StateFlow<DataSource> =
        combine(prefs.mockMode, monitor.state) { mock, status ->
            when {
                mock -> DataSource.MOCK
                status is BackendStatus.Online -> DataSource.BACKEND
                status is BackendStatus.Offline -> DataSource.FALLBACK
                else -> DataSource.CHECKING
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DataSource.CHECKING,
        )
}
