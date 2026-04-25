package com.rescue911.osint.data.repository

import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.MissingCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface Rescue911Repository {
    fun cases(): Flow<List<MissingCase>>
    fun caseById(id: String): Flow<MissingCase?>
    fun evidenceForCase(caseId: String): Flow<List<Evidence>>
    fun hypothesesForCase(caseId: String): Flow<List<Hypothesis>>
    fun auditLog(): Flow<List<AuditEntry>>
}

@Singleton
class MockRescue911Repository @Inject constructor() : Rescue911Repository {
    override fun cases(): Flow<List<MissingCase>> = flow { emit(MockData.cases) }
    override fun caseById(id: String): Flow<MissingCase?> = flow {
        emit(MockData.cases.firstOrNull { it.id == id })
    }
    override fun evidenceForCase(caseId: String): Flow<List<Evidence>> = flow {
        emit(MockData.evidence.filter { it.caseId == caseId })
    }
    override fun hypothesesForCase(caseId: String): Flow<List<Hypothesis>> = flow {
        emit(MockData.hypotheses.filter { it.caseId == caseId })
    }
    override fun auditLog(): Flow<List<AuditEntry>> = flow { emit(MockData.auditLog) }
}
