package com.rescue911.osint.data.repository

import com.rescue911.osint.core.backend.BackendStatusMonitor
import com.rescue911.osint.data.preferences.AppPreferences
import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.MissingCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects between live backend and mock data per call.
 *
 *  Mock mode ON                  -> always Mock (no network, no surprises).
 *  Mock mode OFF + backend OK    -> Backend.
 *  Mock mode OFF + backend down  -> Backend tried; on throw, falls back to
 *                                   Mock so the operator UI keeps working
 *                                   in the field. Status surface stays
 *                                   honest via BackendStatusMonitor.state.
 *
 * Reactive on the [AppPreferences.mockMode] flow: flipping the toggle in
 * Settings re-routes subsequent collections automatically.
 */
@Singleton
class Rescue911RepositoryDispatcher @Inject constructor(
    private val mock: MockRescue911Repository,
    private val backend: BackendRescue911Repository,
    private val prefs: AppPreferences,
    private val monitor: BackendStatusMonitor,
) : Rescue911Repository {

    override fun cases(): Flow<List<MissingCase>> = route(
        backendCall = { backend.cases() },
        mockCall = { mock.cases() },
    )

    override fun caseById(id: String): Flow<MissingCase?> = route(
        backendCall = { backend.caseById(id) },
        mockCall = { mock.caseById(id) },
    )

    override fun evidenceForCase(caseId: String): Flow<List<Evidence>> = route(
        backendCall = { backend.evidenceForCase(caseId) },
        mockCall = { mock.evidenceForCase(caseId) },
    )

    override fun hypothesesForCase(caseId: String): Flow<List<Hypothesis>> = route(
        backendCall = { backend.hypothesesForCase(caseId) },
        mockCall = { mock.hypothesesForCase(caseId) },
    )

    override fun auditLog(): Flow<List<AuditEntry>> = route(
        backendCall = { backend.auditLog() },
        mockCall = { mock.auditLog() },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> route(
        backendCall: () -> Flow<T>,
        mockCall: () -> Flow<T>,
    ): Flow<T> = prefs.mockMode.flatMapLatest { mockOn ->
        if (mockOn) {
            mockCall()
        } else {
            backendCall().catch { _ ->
                runCatching { monitor.check() }
                emitAll(mockCall())
            }
        }
    }
}
