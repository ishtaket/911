package com.rescue911.osint.core.backend

import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.dto.HealthDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackendStatus {
    data object Unknown : BackendStatus
    data object Checking : BackendStatus
    data class Online(val info: HealthDto) : BackendStatus
    data class Offline(val reason: String) : BackendStatus
}

/**
 * Hits /v1/health on demand and exposes the result as a StateFlow.
 *
 * Used by:
 *   - SettingsScreen to render an online / offline pill + "Check" button.
 *   - Rescue911RepositoryDispatcher to decide between live backend and the
 *     mock fallback when the user has Mock mode off.
 */
@Singleton
class BackendStatusMonitor @Inject constructor(
    private val api: Rescue911Api,
) {
    private val _state = MutableStateFlow<BackendStatus>(BackendStatus.Unknown)
    val state: StateFlow<BackendStatus> = _state.asStateFlow()

    val isOnline: Boolean get() = _state.value is BackendStatus.Online

    suspend fun check(): BackendStatus {
        _state.value = BackendStatus.Checking
        val next = try {
            BackendStatus.Online(api.health())
        } catch (t: Throwable) {
            BackendStatus.Offline(t.javaClass.simpleName + ": " + (t.message ?: ""))
        }
        _state.value = next
        return next
    }
}
