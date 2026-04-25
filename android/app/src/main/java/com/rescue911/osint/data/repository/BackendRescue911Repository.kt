package com.rescue911.osint.data.repository

import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.mapper.toDomain
import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.MissingCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live backend implementation. Calls our own FastAPI service only — no
 * external provider keys, no third-party SDKs, no scraping logic on device.
 *
 * Throws on network/parse errors so the dispatcher can fall back to mock.
 */
@Singleton
class BackendRescue911Repository @Inject constructor(
    private val api: Rescue911Api,
) : Rescue911Repository {

    override fun cases(): Flow<List<MissingCase>> = flow {
        emit(api.listCases().map { it.toDomain() })
    }

    override fun caseById(id: String): Flow<MissingCase?> = flow {
        val dto = runCatching { api.getCase(id) }.getOrNull()
        emit(dto?.toDomain())
    }

    override fun evidenceForCase(caseId: String): Flow<List<Evidence>> = flow {
        emit(api.listEvidence(caseId).map { it.toDomain() })
    }

    override fun hypothesesForCase(caseId: String): Flow<List<Hypothesis>> = flow {
        emit(api.listHypotheses(caseId).map { it.toDomain() })
    }

    override fun auditLog(): Flow<List<AuditEntry>> = flow {
        emit(api.listAudit().map { it.toDomain() })
    }
}
